package com.example.shipping;

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
    private String email;
    private int quantity;
    private ShipmentStatus status;
    /** Set when a WAREHOUSE-role user reports an issue instead of confirming the pick. */
    private String issueReason;
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

    /**
     * PENDING_WAREHOUSE creation path — also keeps email/quantity around since the WAREHOUSE-role
     * confirm/report-issue action that eventually resolves this shipment runs long after the
     * original Kafka listener call (and its method-local variables) has returned.
     */
    public Shipment(String orderId, String keycloakUserId, String email, int quantity,
            ShipmentStatus status, ShippingCarrier carrier, BigDecimal cost) {
        this(orderId, keycloakUserId, status, carrier, cost);
        this.email = email;
        this.quantity = quantity;
    }

    /** Appends the next tracking stage; called on creation and by the periodic tracking-progression job. */
    void advanceTracking(TrackingStatus next) {
        this.trackingStatus = next;
        this.trackingHistory.add(new TrackingEvent(next, Instant.now()));
    }

    void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    void setIssueReason(String issueReason) {
        this.issueReason = issueReason;
    }

    void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
