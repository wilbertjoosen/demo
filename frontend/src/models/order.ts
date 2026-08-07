import type { Address } from './address'
import type { PaymentMethod } from './payment'
import type { ShippingCarrier } from './shipment'

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
  paymentMethod: PaymentMethod | null
  shippingCarrier: ShippingCarrier | null
  status: OrderStatus
  createdAt: string
  updatedAt: string
  deleted: boolean
}
