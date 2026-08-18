package com.example.reporting.model;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

/**
 * Denormalized read model for one order, folded entirely from order-events (ORDER_CREATED,
 * ORDER_CANCELLED) by the Kafka Streams KTable aggregator in ReportingTopology — no synchronous
 * call into order-service. {@code failureStage} is filled in separately, via a table-table left
 * join against a KTable built from payment/shipping/delivery-events, so a cancelled order can be
 * attributed to the saga step that actually failed.
 *
 * <p>{@code confirmedAt} stays null on this branch — unlike main, order-service here has no
 * generic status-advance event for a successful confirmation, only ORDER_CREATED and
 * ORDER_CANCELLED, so there's no signal to derive it from. The field is kept (rather than removed)
 * so the report models/frontend panels ported from main don't need their own shape change.
 *
 * <p>Mutated and returned in place by both the aggregator and the join (rather than rebuilt each
 * time) — safe here because Kafka Streams serializes the returned value to the state store
 * immediately, so no stale in-memory reference is ever shared across store reads.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderMetric {

    private String orderId;
    private String userId;
    private String email;
    private String productId;
    private int quantity;
    private String paymentMethod;
    private String shippingCarrier;
    private String status;
    private String failureStage;
    private Instant orderCreatedAt;
    private Instant confirmedAt;
    private Instant cancelledAt;

    public OrderMetric apply(DomainEvent event) {
        Map<?, ?> payload = (Map<?, ?>) event.payload();
        if (EventTypes.ORDER_CREATED.equals(event.eventType())) {
            this.orderId = event.orderId();
            this.userId = (String) payload.get("userId");
            this.email = (String) payload.get("email");
            this.productId = (String) payload.get("productId");
            this.quantity = ((Number) payload.get("quantity")).intValue();
            this.paymentMethod = (String) payload.get("paymentMethod");
            this.shippingCarrier = (String) payload.get("shippingCarrier");
            this.status = "PENDING_PAYMENT";
            this.orderCreatedAt = event.timestamp();
        } else if (EventTypes.ORDER_CANCELLED.equals(event.eventType())) {
            this.status = "CANCELLED";
            this.cancelledAt = event.timestamp();
        }
        return this;
    }

    public OrderMetric withFailureStage(String stage) {
        this.failureStage = stage;
        return this;
    }
}
