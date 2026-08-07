import { http } from './http'
import { unwrapCollection } from './hal'
import type { MediaAsset, MediaType } from '../models'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string

export const mediaApi = {
  async listByProduct(productId: string): Promise<MediaAsset[]> {
    const { data } = await http.get('/api/media', { params: { productId } })
    return unwrapCollection<MediaAsset>(data)
  },
  /** Returns an absolute URL — the backend hands back a path relative to its own routing prefix. */
  async upload(file: File): Promise<string> {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await http.post('/api/media/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return `${API_BASE_URL}${data.url}`
  },
  async create(payload: { productId: string; type: MediaType; url: string; caption?: string }): Promise<MediaAsset> {
    const { data } = await http.post('/api/media', payload)
    return data
  },
  async remove(id: string): Promise<void> {
    await http.delete(`/api/media/${id}`)
  },
}
