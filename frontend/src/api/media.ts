import { http } from './http'
import { unwrapCollection } from './hal'
import type { MediaAsset, MediaType } from '../models'

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
}
