package com.example.payment;

import java.util.List;

public interface PaymentService {

    /**
     * Simulates charging for the order; publishes PAYMENT_COMPLETED or PAYMENT_FAILED.
     * {@code quantity} is carried forward into the published event's payload — event-carried
     * state transfer, so downstream saga participants (shipping/delivery) don't need a synchronous
     * call back to order-service just to know what they're shipping.
     */
    void charge(String orderId, String email, int quantity);

    /** Compensating action: publishes PAYMENT_REFUNDED for an already-completed payment. */
    void refund(String orderId);

    List<Payment> list();

    /** Soft delete — an admin record-keeping action, does not touch the underlying payment/refund status. */
    void delete(String id);
}
