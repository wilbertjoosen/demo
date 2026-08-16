package com.example.delivery.model;
import com.example.delivery.enums.DeliveryStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "deliveries")
@Getter
@NoArgsConstructor
public class Delivery {

    @Id
    private String id;

    private String orderId;
    private DeliveryStatus status;
    /** Set when a DELIVERY_AGENT-role user reports an issue instead of confirming delivery. */
    private String issueReason;
    private String email;
    private int quantity;

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

    public Delivery(String orderId, DeliveryStatus status) {
        this.orderId = orderId;
        this.status = status;
    }

    /**
     * PENDING_DELIVERY_AGENT creation path — also keeps email/quantity around since the
     * DELIVERY_AGENT-role confirm/report-issue action that eventually resolves this delivery runs
     * long after the original Kafka listener call (and its method-local variables) has returned.
     */
    public Delivery(String orderId, DeliveryStatus status, String email, int quantity) {
        this.orderId = orderId;
        this.status = status;
        this.email = email;
        this.quantity = quantity;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public void setIssueReason(String issueReason) {
        this.issueReason = issueReason;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
