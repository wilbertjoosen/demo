import { http } from './http'
import { unwrapCollection } from './hal'
import type { Product } from '../models'

export const productsApi = {
  async list(): Promise<Product[]> {
    const { data } = await http.get('/api/products')
    return unwrapCollection<Product>(data)
  },
  async create(payload: { sku: string; name: string; price: number }): Promise<Product> {
    const { data } = await http.post('/api/products', payload)
    return data
  },
  async update(id: string, payload: Partial<Pick<Product, 'sku' | 'name' | 'price'>>): Promise<Product> {
    const { data } = await http.put(`/api/products/${id}`, payload)
    return data
  },
  async remove(id: string): Promise<void> {
    await http.delete(`/api/products/${id}`)
  },
}
