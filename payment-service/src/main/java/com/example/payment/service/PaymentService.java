package com.example.payment.service;
import com.example.payment.controller.PaymentController;
import com.example.payment.model.Payment;

import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;

import java.util.List;
import java.util.Map;

public interface PaymentService {

    /**
     * Simulates charging for the order; publishes PAYMENT_COMPLETED or PAYMENT_FAILED.
     * {@code quantity}/{@code shippingCarrier} are carried forward into the published event's
     * payload — event-carried state transfer, so downstream saga participants (shipping/delivery)
     * don't need a synchronous call back to order-service just to know what they're shipping and how.
     * If {@code method}'s mock gateway is currently unavailable (see {@link PaymentGatewayAvailability}),
     * fails immediately without attempting the charge, same as a real integration would refuse to
     * call a processor its own health check already reported down.
     */
    void charge(String orderId, String keycloakUserId, String email, int quantity, PaymentMethod method, ShippingCarrier shippingCarrier);

    /** Compensating action: publishes PAYMENT_REFUNDED for an already-completed payment. */
    void refund(String orderId);

    List<Payment> list();

    Payment getByOrderId(String orderId);

    /**
     * Records the URL of an already-stored proof-of-payment file on the customer's own payment —
     * bank transfer receipt or cash confirmation, uploaded via PaymentController/PaymentProofStorageService.
     * Throws if the payment isn't {@code keycloakUserId}'s own.
     */
    Payment attachProof(String keycloakUserId, String id, String url);

    /** Admin-only soft delete — record-keeping action, does not touch the underlying payment/refund status. */
    void delete(String id);

    /**
     * FINANCE-role approval for a BANK_TRANSFER/CASH payment sitting in AWAITING_REVIEW — marks it
     * COMPLETED and publishes PAYMENT_COMPLETED, same as an instant-gateway charge would.
     * Throws if the payment isn't currently AWAITING_REVIEW.
     */
    void approve(String id);

    /**
     * FINANCE-role rejection for a BANK_TRANSFER/CASH payment sitting in AWAITING_REVIEW — marks it
     * FAILED with {@code reason} and publishes PAYMENT_FAILED, triggering the same compensation path
     * a declined instant charge would. Throws if the payment isn't currently AWAITING_REVIEW.
     */
    void reject(String id, String reason);

    /** Current mock availability per payment method, used to enable/disable methods in checkout UI. */
    Map<PaymentMethod, Boolean> availableMethods();

    /** Admin-only: force a method's mock gateway up/down to demonstrate the unavailable path. */
    void setMethodAvailability(PaymentMethod method, boolean available);
}
