package com.example.shipping.service;

import com.example.common.model.ShippingCarrier;
import com.example.shipping.model.Shipment;

import java.math.BigDecimal;
import java.util.List;

public interface ShippingService {

    /**
     * Creates the shipment as PENDING_WAREHOUSE (or, for the simulated stockout quantity, fails
     * immediately without needing review) — a WAREHOUSE-role user must {@link #confirmPicked} or
     * {@link #reportIssue} before SHIPPED/SHIPPING_FAILED is published.
     */
    void ship(String orderId, String keycloakUserId, String email, int quantity, ShippingCarrier carrier);

    /** Mock carrier rate quote, used by the frontend at checkout before an order/shipment exists. */
    BigDecimal quote(ShippingCarrier carrier, int quantity);

    Shipment getByOrderId(String orderId);

    List<Shipment> list();

    void delete(String id);

    /**
     * WAREHOUSE-role confirmation that a PENDING_WAREHOUSE shipment has been picked and handed to
     * the carrier — marks it DISPATCHED, starts tracking, and publishes SHIPPED. Throws if the
     * shipment isn't currently PENDING_WAREHOUSE.
     */
    void confirmPicked(String id);

    /**
     * WAREHOUSE-role report that a PENDING_WAREHOUSE shipment can't be fulfilled (damaged stock,
     * short pick, etc.) — marks it FAILED and publishes SHIPPING_FAILED, triggering the same
     * compensation path (refund + stock release) an automatic failure would. Throws if the
     * shipment isn't currently PENDING_WAREHOUSE.
     */
    void reportIssue(String id, String reason);
}
