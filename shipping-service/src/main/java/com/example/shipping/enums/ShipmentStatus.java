package com.example.shipping.enums;

public enum ShipmentStatus {
    /** Awaiting a WAREHOUSE-role pick confirmation (or issue report) before it can dispatch. */
    PENDING_WAREHOUSE,
    DISPATCHED,
    FAILED
}
