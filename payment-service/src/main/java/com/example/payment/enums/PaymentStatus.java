package com.example.payment.enums;


public enum PaymentStatus {
    /** BANK_TRANSFER/CASH only — awaiting the mock processing delay (see PaymentServiceImpl). */
    PENDING,
    /** BANK_TRANSFER/CASH only — mock delay elapsed, now waiting on a FINANCE-role approve/reject. */
    AWAITING_REVIEW,
    COMPLETED,
    FAILED,
    REFUNDED
}
