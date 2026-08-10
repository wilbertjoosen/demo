export type PaymentMethod = 'CREDIT_CARD' | 'DEBIT_CARD' | 'PAYPAL' | 'PIX' | 'BOLETO' | 'BANK_TRANSFER' | 'CASH'

export type PaymentMethodAvailability = Record<PaymentMethod, boolean>

export type PaymentStatus = 'PENDING' | 'AWAITING_REVIEW' | 'COMPLETED' | 'FAILED' | 'REFUNDED'

export interface Payment {
  id: string
  orderId: string
  email: string
  method: PaymentMethod
  status: PaymentStatus
  failureReason: string | null
  quantity: number
  keycloakUserId: string | null
  shippingCarrier: string | null
  proofOfPaymentUrl: string | null
  createdAt: string
  updatedAt: string
}
