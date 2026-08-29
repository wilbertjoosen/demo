export type MediaType = 'PHOTO' | 'VIDEO' | 'DOCUMENT'

const PHOTO_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp']
const VIDEO_EXTENSIONS = ['mp4', 'webm', 'mov']

/** Matches product-media-service's own allowed-extension list — kept in sync manually, both sides are small. */
export function detectMediaType(filename: string): MediaType {
  const extension = filename.split('.').pop()?.toLowerCase() ?? ''
  if (PHOTO_EXTENSIONS.includes(extension)) return 'PHOTO'
  if (VIDEO_EXTENSIONS.includes(extension)) return 'VIDEO'
  return 'DOCUMENT'
}

export type MediaValidationStatus = 'PENDING_VALIDATION' | 'ACTIVE' | 'REJECTED'

export interface MediaAsset {
  id: string
  productId: string
  type: MediaType
  url: string
  fileName: string
  caption: string | null
  position: number
  createdAt: string
  updatedAt: string
  deleted: boolean
  validationStatus: MediaValidationStatus
}

/** One presigned URL per S3 multipart upload part. */
export interface PresignedUploadPart {
  partNumber: number
  uploadUrl: string
}

/**
 * Exactly one shape is populated, matching product-media-service's PresignedUploadResponse:
 * `uploadUrl` alone for a single-PUT upload, or `uploadId`/`parts` for a multipart one.
 */
export interface PresignedUploadResponse {
  key: string
  uploadUrl: string | null
  uploadId: string | null
  parts: PresignedUploadPart[] | null
  partSizeBytes: number | null
}
