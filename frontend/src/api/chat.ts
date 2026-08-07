import { http } from './http'
import { unwrapCollection } from './hal'
import type { ChatMessage } from '../models'

export const chatApi = {
  async history(productId: string, limit = 200): Promise<ChatMessage[]> {
    const { data } = await http.get('/api/chat/messages', { params: { productId, limit } })
    return unwrapCollection<ChatMessage>(data)
  },
}
