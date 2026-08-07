export type PaymentMethod = 'CREDIT_CARD' | 'DEBIT_CARD' | 'PAYPAL' | 'PIX' | 'BOLETO'

export type PaymentMethodAvailability = Record<PaymentMethod, boolean>
