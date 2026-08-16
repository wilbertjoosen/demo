package com.example.shipping.model;

import com.example.shipping.enums.ShipmentStatus;
import com.example.shipping.enums.TrackingStatus;

import com.example.common.model.ShippingCarrier;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "shipments")
@Getter
@NoArgsConstructor
public class Shipment {

    @Id
    private String id;

    private String orderId;
    private String keycloakUserId;
    private ShipmentStatus status;
    private ShippingCarrier carrier;
    private BigDecimal cost;
    private TrackingStatus trackingStatus;
    private List<TrackingEvent> trackingHistory = new ArrayList<>();

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

    public Shipment(String orderId, String keycloakUserId, ShipmentStatus status, ShippingCarrier carrier, BigDecimal cost) {
        this.orderId = orderId;
        this.keycloakUserId = keycloakUserId;
        this.status = status;
        this.carrier = carrier;
        this.cost = cost;
        if (status == ShipmentStatus.DISPATCHED) {
            advanceTracking(TrackingStatus.LABEL_CREATED);
        }
    }

    /** Appends the next tracking stage; called on creation and by the periodic tracking-progression job. */
    public void advanceTracking(TrackingStatus next) {
        this.trackingStatus = next;
        this.trackingHistory.add(new TrackingEvent(next, Instant.now()));
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
