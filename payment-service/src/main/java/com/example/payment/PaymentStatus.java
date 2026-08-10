package com.example.payment;

public enum PaymentStatus {
    /** BANK_TRANSFER/CASH only — awaiting the mock processing delay (see PaymentServiceImpl). */
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}
