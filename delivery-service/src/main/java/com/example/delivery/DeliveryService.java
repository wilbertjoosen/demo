package com.example.delivery;

import java.util.List;

public interface DeliveryService {

    /**
     * Creates the delivery as PENDING_DELIVERY_AGENT (or, for the simulated failure quantity,
     * fails immediately without needing review) — a DELIVERY_AGENT-role user must
     * {@link #confirmDelivered} or {@link #reportIssue} before DELIVERED/DELIVERY_FAILED is published.
     */
    void deliver(String orderId, String email, int quantity);

    List<Delivery> list();

    void delete(String id);

    /**
     * DELIVERY_AGENT-role confirmation that a PENDING_DELIVERY_AGENT delivery was handed to the
     * customer — marks it DELIVERED and publishes DELIVERED. Throws if the delivery isn't currently
     * PENDING_DELIVERY_AGENT.
     */
    void confirmDelivered(String id);

    /**
     * DELIVERY_AGENT-role report that a PENDING_DELIVERY_AGENT delivery couldn't be completed
     * (customer unavailable, wrong address, etc.) — marks it FAILED and publishes DELIVERY_FAILED,
     * triggering the same compensation path a system failure would. Throws if the delivery isn't
     * currently PENDING_DELIVERY_AGENT.
     */
    void reportIssue(String id, String reason);
}
