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

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
