import { http } from './http'
import { unwrapCollection } from './hal'
import type { MediaAsset, MediaType, PresignedUploadResponse } from '../models'

export const mediaApi = {
  async listByProduct(productId: string): Promise<MediaAsset[]> {
    const { data } = await http.get('/api/media', { params: { productId } })
    return unwrapCollection<MediaAsset>(data)
  },
  async upload(file: File): Promise<MediaAsset> {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await http.post('/api/media/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },
  async create(payload: { productId: string; type: MediaType; url: string; fileName: string, caption?: string }): Promise<MediaAsset> {
    const { data } = await http.post('/api/media', payload)
    return data
  },
  async remove(id: string): Promise<void> {
    await http.delete(`/api/media/${id}`)
  },

  // Presigned direct-to-S3 upload — see composables/usePresignedMediaUpload.ts for the orchestration
  // (single PUT vs. real S3 multipart, chosen server-side by file size).
  async presignUpload(payload: { fileName: string; contentType: string; fileSizeBytes: number }): Promise<PresignedUploadResponse> {
    const { data } = await http.post('/api/media/presigned-upload', payload)
    return data
  },
  async completeMultipartUpload(payload: { key: string; uploadId: string; parts: { partNumber: number; eTag: string }[] }): Promise<void> {
    await http.post('/api/media/presigned-upload/complete', payload)
  },
  async abortMultipartUpload(key: string, uploadId: string): Promise<void> {
    await http.delete('/api/media/presigned-upload', { params: { key, uploadId } })
  },
  async confirmStagedUpload(payload: {
    productId: string
    type: MediaType
    key: string
    fileName: string
    caption?: string
  }): Promise<MediaAsset> {
    const { data } = await http.post('/api/media/presigned-upload/confirm', payload)
    return data
  },
}
