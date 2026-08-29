import axios from 'axios'
import { mediaApi } from '../api/media'
import type { MediaAsset, MediaType, PresignedUploadPart } from '../models'

/**
 * Deliberately NOT the shared `http` instance (api/http.ts) — that one's request interceptor
 * attaches our own Keycloak Bearer token to every request, which must never go to S3: presigned
 * URLs already carry their own auth (as query parameters), and leaking our app's JWT to a
 * third-party host on every upload would be a real credential-exposure bug, not just noise.
 */
const s3 = axios.create()

/** Parts upload in parallel, but not unbounded — keeps a big file from opening dozens of connections at once. */
const MAX_CONCURRENT_PART_UPLOADS = 3

export interface UploadProgress {
  /** 0-100. For multipart, this is "parts finished / total parts", not byte-accurate. */
  percent: number
  stage: 'uploading' | 'confirming'
}

async function uploadSinglePart(uploadUrl: string, blob: Blob, contentType: string): Promise<void> {
  await s3.put(uploadUrl, blob, { headers: { 'Content-Type': contentType } })
}

/** @returns the S3-assigned ETag for this part — required by CompleteMultipartUpload to identify each part. */
async function uploadOnePart(part: PresignedUploadPart, blob: Blob): Promise<{ partNumber: number; eTag: string }> {
  const response = await s3.put(part.uploadUrl, blob)
  const eTag = response.headers.etag ?? response.headers.ETag
  if (!eTag) {
    // Needs the S3 bucket's CORS config to include ETag in ExposeHeaders — otherwise the browser
    // silently strips it from a cross-origin response and this throws every time, not just on a
    // real misconfiguration, so it's worth failing loudly rather than sending a blank ETag on to
    // CompleteMultipartUpload (which S3 would then reject anyway, just with a much less clear error).
    throw new Error(
      `S3 did not return an ETag for part ${part.partNumber} — check the staging bucket's CORS ` +
        'config exposes the ETag header (ExposeHeaders: ["ETag"]).',
    )
  }
  return { partNumber: part.partNumber, eTag }
}

async function uploadPartsWithBoundedConcurrency(
  file: File,
  parts: PresignedUploadPart[],
  partSizeBytes: number,
  onProgress?: (percent: number) => void,
): Promise<{ partNumber: number; eTag: string }[]> {
  const results: { partNumber: number; eTag: string }[] = new Array(parts.length)
  let nextIndex = 0
  let completed = 0

  async function worker(): Promise<void> {
    while (nextIndex < parts.length) {
      const index = nextIndex++
      const part = parts[index]
      const start = index * partSizeBytes
      const blob = file.slice(start, Math.min(start + partSizeBytes, file.size))
      results[index] = await uploadOnePart(part, blob)
      completed++
      onProgress?.(Math.round((completed / parts.length) * 100))
    }
  }

  await Promise.all(Array.from({ length: Math.min(MAX_CONCURRENT_PART_UPLOADS, parts.length) }, worker))
  return results
}

/**
 * Full direct-to-S3 upload: presign -> PUT straight to S3 (single request or real multipart,
 * decided server-side by file size) -> confirm. The returned MediaAsset is PENDING_VALIDATION —
 * it won't appear in mediaApi.listByProduct() until MediaValidationListener (backend) inspects the
 * actual bytes and promotes it to ACTIVE, so callers should treat this as "queued", not "done".
 */
export async function uploadMediaDirectToS3(
  file: File,
  target: { productId: string; type: MediaType; caption?: string },
  onProgress?: (progress: UploadProgress) => void,
): Promise<MediaAsset> {
  const presigned = await mediaApi.presignUpload({
    fileName: file.name,
    contentType: file.type || 'application/octet-stream',
    fileSizeBytes: file.size,
  })

  if (presigned.uploadUrl) {
    await uploadSinglePart(presigned.uploadUrl, file, file.type || 'application/octet-stream')
    onProgress?.({ percent: 100, stage: 'uploading' })
  } else if (presigned.uploadId && presigned.parts && presigned.partSizeBytes) {
    try {
      const completedParts = await uploadPartsWithBoundedConcurrency(
        file,
        presigned.parts,
        presigned.partSizeBytes,
        (percent) => onProgress?.({ percent, stage: 'uploading' }),
      )
      await mediaApi.completeMultipartUpload({ key: presigned.key, uploadId: presigned.uploadId, parts: completedParts })
    } catch (error) {
      // Best-effort — AbandonedUploadCleanupJob (backend, hourly by default) is the real backstop
      // if this itself fails, so a failed abort here isn't left as a permanent storage leak.
      await mediaApi.abortMultipartUpload(presigned.key, presigned.uploadId).catch(() => undefined)
      throw error
    }
  } else {
    throw new Error('presignUpload response had neither a single uploadUrl nor a multipart session')
  }

  onProgress?.({ percent: 100, stage: 'confirming' })
  return mediaApi.confirmStagedUpload({
    productId: target.productId,
    type: target.type,
    key: presigned.key,
    fileName: file.name,
    caption: target.caption,
  })
}
