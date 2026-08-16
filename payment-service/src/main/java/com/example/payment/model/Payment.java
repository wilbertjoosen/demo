package com.example.payment.model;

import com.example.payment.enums.PaymentStatus;

import com.example.common.model.PaymentMethod;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "payments")
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    private String id;

    private String orderId;
    private String email;
    private PaymentMethod method;
    private PaymentStatus status;
    /** Set when status is FAILED because the method's mock gateway was unavailable, rather than a declined charge. */
    private String failureReason;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;

    private boolean deleted = false;
    private Instant deletedAt;

    public Payment(String orderId, String email, PaymentMethod method, PaymentStatus status, String failureReason) {
        this.orderId = orderId;
        this.email = email;
        this.method = method;
        this.status = status;
        this.failureReason = failureReason;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
