export type DeliveryStatus = 'PENDING_DELIVERY_AGENT' | 'DELIVERED' | 'FAILED'

export interface Delivery {
  id: string
  orderId: string
  email: string | null
  quantity: number
  status: DeliveryStatus
  issueReason: string | null
  createdAt: string
  updatedAt: string
}
