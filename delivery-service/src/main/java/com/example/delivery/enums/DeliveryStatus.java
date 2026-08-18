package com.example.delivery.enums;

public enum DeliveryStatus {
    /** Awaiting a DELIVERY_AGENT-role confirmation (or issue report) of last-mile delivery. */
    PENDING_DELIVERY_AGENT,
    DELIVERED,
    FAILED
}
