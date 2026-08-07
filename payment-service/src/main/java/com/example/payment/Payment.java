package com.example.payment;

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
    private PaymentStatus status;

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

    public Payment(String orderId, String email, PaymentStatus status) {
        this.orderId = orderId;
        this.email = email;
        this.status = status;
    }

    void setStatus(PaymentStatus status) {
        this.status = status;
    }

    void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
