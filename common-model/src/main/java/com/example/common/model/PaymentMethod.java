package com.example.common.model;

public enum PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    PAYPAL,
    PIX,
    BOLETO,
    /** No instant gateway to charge — resolves after a mock processing delay instead. */
    BANK_TRANSFER,
    /** Same as BANK_TRANSFER: no instant gateway, resolves after a mock processing delay. */
    CASH
}
