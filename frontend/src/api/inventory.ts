import { http } from './http'
import type { InventoryAggregate } from '../models'

export const inventoryApi = {
  async aggregate(productId: string): Promise<InventoryAggregate> {
    const { data } = await http.get(`/api/inventory/${productId}`)
    return data
  },
  async addStock(productId: string, warehouseId: string, quantity: number): Promise<void> {
    await http.post(`/api/inventory/${productId}/stock`, { warehouseId, quantity })
  },
}
