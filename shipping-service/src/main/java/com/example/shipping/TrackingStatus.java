package com.example.shipping;

/** Carrier-facing tracking stages, distinct from {@link ShipmentStatus} (the saga's own success/failure outcome). */
public enum TrackingStatus {
    LABEL_CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED
}
