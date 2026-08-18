import { http } from './http'
import { unwrapCollection } from './hal'
import type { Delivery } from '../models'

export const deliveriesApi = {
  async list(): Promise<Delivery[]> {
    const { data } = await http.get('/api/deliveries')
    return unwrapCollection<Delivery>(data)
  },
  async confirmDelivered(id: string): Promise<void> {
    await http.post(`/api/deliveries/${id}/confirm-delivered`)
  },
  async reportIssue(id: string, reason?: string): Promise<void> {
    await http.post(`/api/deliveries/${id}/report-issue`, reason ? { reason } : undefined)
  },
}
