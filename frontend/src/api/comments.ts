import { http } from './http'
import { unwrapCollection } from './hal'
import type { Comment } from '../models'

export const commentsApi = {
  async listByProduct(productId: string): Promise<Comment[]> {
    const { data } = await http.get('/api/comments', { params: { productId } })
    return unwrapCollection<Comment>(data)
  },
  async create(payload: { productId: string; parentId?: string | null; body: string }): Promise<Comment> {
    const { data } = await http.post('/api/comments', payload)
    return data
  },
  async update(id: string, body: string): Promise<Comment> {
    const { data } = await http.put(`/api/comments/${id}`, { body })
    return data
  },
  async remove(id: string): Promise<void> {
    await http.delete(`/api/comments/${id}`)
  },
}
