import { http } from './http'
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
}
