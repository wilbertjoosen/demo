import { http } from './http'
import { unwrapCollection } from './hal'
import type { Shipment, ShipmentQuote, ShippingCarrier } from '../models'

export const shipmentsApi = {
  async quote(carrier: ShippingCarrier, quantity: number): Promise<ShipmentQuote> {
    const { data } = await http.get('/api/shipments/quote', { params: { carrier, quantity } })
    return data
  },
  async tracking(orderId: string): Promise<Shipment> {
    const { data } = await http.get(`/api/shipments/order/${orderId}/tracking`)
    return data
  },
  async list(): Promise<Shipment[]> {
    const { data } = await http.get('/api/shipments')
    return unwrapCollection<Shipment>(data)
  },
  async confirmPicked(id: string): Promise<void> {
    await http.post(`/api/shipments/${id}/confirm-picked`)
  },
  async reportIssue(id: string, reason?: string): Promise<void> {
    await http.post(`/api/shipments/${id}/report-issue`, reason ? { reason } : undefined)
  },
}
