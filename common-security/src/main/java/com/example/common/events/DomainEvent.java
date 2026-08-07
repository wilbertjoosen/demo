package com.example.common.events;

import java.time.Instant;

/**
 * Shared Kafka message shape across every topic in the saga (deliberately simple, no schema registry).
 */
public record DomainEvent(String eventType, String orderId, Object payload, Instant timestamp) {

    public static DomainEvent of(String eventType, String orderId, Object payload) {
        return new DomainEvent(eventType, orderId, payload, Instant.now());
    }
}
