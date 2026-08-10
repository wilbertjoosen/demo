export type PaymentMethod = 'CREDIT_CARD' | 'DEBIT_CARD' | 'PAYPAL' | 'PIX' | 'BOLETO' | 'BANK_TRANSFER' | 'CASH'

export type PaymentMethodAvailability = Record<PaymentMethod, boolean>
