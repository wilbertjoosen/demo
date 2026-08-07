import type { Address } from './address'

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'SHIPPED'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'PAYMENT_FAILED'
  | 'SHIPPING_FAILED'
  | 'DELIVERY_FAILED'

export interface OrderView {
  id: string
  keycloakUserId: string
  productId: string
  quantity: number
  shippingAddress: Address | null
  status: OrderStatus
  createdAt: string
  updatedAt: string
  deleted: boolean
}
