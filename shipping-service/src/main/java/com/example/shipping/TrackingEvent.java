package com.example.shipping;

import lombok.Getter;

import java.time.Instant;

/** One entry in a shipment's public tracking history, mimicking what a carrier's own tracking page shows. */
@Getter
public class TrackingEvent {

    private final TrackingStatus status;
    private final Instant timestamp;

    public TrackingEvent(TrackingStatus status, Instant timestamp) {
        this.status = status;
        this.timestamp = timestamp;
    }
}
