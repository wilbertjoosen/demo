package com.example.payment.model;
import com.example.payment.enums.PaymentStatus;

import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "payments")
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    private String id;

    /** findByOrderId(orderId). */
    @Indexed
    private String orderId;
    private String email;
    private PaymentMethod method;
    /** findByStatus(status). */
    @Indexed
    private PaymentStatus status;
    /** Set when status is FAILED because the method's mock gateway was unavailable, rather than a declined charge. */
    private String failureReason;

    /**
     * Only populated for PENDING payments (BANK_TRANSFER/CASH) — the saga's ORDER_CREATED fields
     * that PAYMENT_COMPLETED/PAYMENT_FAILED need, kept around because the scheduled job that
     * eventually resolves this payment runs long after the original Kafka listener call (and its
     * method-local variables) has returned.
     */
    private int quantity;
    private String keycloakUserId;
    private ShippingCarrier shippingCarrier;
    /** Customer-uploaded bank transfer/cash receipt — a path served by this service's own /api/payments/files/**. */
    private String proofOfPaymentUrl;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;

    @Indexed
    private boolean deleted = false;
    private Instant deletedAt;

    public Payment(String orderId, String email, PaymentMethod method, PaymentStatus status, String failureReason) {
        this.orderId = orderId;
        this.email = email;
        this.method = method;
        this.status = status;
        this.failureReason = failureReason;
    }

    public Payment(String orderId, String email, PaymentMethod method, PaymentStatus status,
            int quantity, String keycloakUserId, ShippingCarrier shippingCarrier) {
        this.orderId = orderId;
        this.email = email;
        this.method = method;
        this.status = status;
        this.quantity = quantity;
        this.keycloakUserId = keycloakUserId;
        this.shippingCarrier = shippingCarrier;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public void setProofOfPaymentUrl(String proofOfPaymentUrl) {
        this.proofOfPaymentUrl = proofOfPaymentUrl;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
