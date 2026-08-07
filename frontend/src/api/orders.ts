import { http } from './http'
import { unwrapCollection } from './hal'
import type { Address, OrderView } from '../models'

export const ordersApi = {
  async list(): Promise<OrderView[]> {
    const { data } = await http.get('/api/orders')
    return unwrapCollection<OrderView>(data)
  },
  async get(id: string): Promise<OrderView> {
    const { data } = await http.get(`/api/orders/${id}`)
    return data
  },
  async place(payload: { productId: string; quantity: number; shippingAddress: Address }): Promise<OrderView> {
    const { data } = await http.post('/api/orders', payload)
    return data
  },
  async cancel(id: string): Promise<OrderView> {
    const { data } = await http.post(`/api/orders/${id}/cancel`)
    return data
  },
  async updateAddress(id: string, shippingAddress: Address): Promise<OrderView> {
    const { data } = await http.patch(`/api/orders/${id}`, { shippingAddress })
    return data
  },
  async remove(id: string): Promise<void> {
    await http.delete(`/api/orders/${id}`)
  },
}
