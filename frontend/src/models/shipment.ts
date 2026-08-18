export type ShippingCarrier = 'UPS' | 'DHL'

export type TrackingStatus = 'LABEL_CREATED' | 'PICKED_UP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED'

export interface TrackingEvent {
  status: TrackingStatus
  timestamp: string
}

export interface ShipmentQuote {
  carrier: ShippingCarrier
  cost: number
}

export type ShipmentStatus = 'PENDING_WAREHOUSE' | 'DISPATCHED' | 'FAILED'

export interface Shipment {
  id: string
  orderId: string
  email: string | null
  quantity: number
  status: ShipmentStatus
  issueReason: string | null
  carrier: ShippingCarrier
  cost: number
  trackingStatus: TrackingStatus | null
  trackingHistory: TrackingEvent[]
  createdAt: string
  updatedAt: string
}
