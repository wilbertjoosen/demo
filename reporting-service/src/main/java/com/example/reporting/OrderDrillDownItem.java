package com.example.reporting;

import java.time.Instant;

public record OrderDrillDownItem(
        String orderId,
        String email,
        String productId,
        int quantity,
        String status,
        String paymentMethod,
        String shippingCarrier,
        Instant orderCreatedAt) {
}
