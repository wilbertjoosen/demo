import { http } from './http'
import type { ConversationSummary, DirectMessage } from '../models'

export const conversationsApi = {
  async list(): Promise<ConversationSummary[]> {
    const { data } = await http.get('/api/conversations')
    return data
  },
  /** Idempotent: returns the existing conversation with this user if one already exists. */
  async start(otherUserId: string, otherUsername: string): Promise<{ id: string }> {
    const { data } = await http.post('/api/conversations', { otherUserId, otherUsername })
    return data
  },
  async messages(conversationId: string, limit = 100): Promise<DirectMessage[]> {
    const { data } = await http.get(`/api/conversations/${conversationId}/messages`, { params: { limit } })
    return data
  },
}
