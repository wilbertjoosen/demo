package com.example.shipping.service;

import com.example.shipping.model.Shipment;

import com.example.common.model.ShippingCarrier;

import java.math.BigDecimal;
import java.util.List;

public interface ShippingService {

    /** Simulates warehouse pick + carrier handoff; publishes SHIPPED or SHIPPING_FAILED. */
    void ship(String orderId, String keycloakUserId, String email, int quantity, ShippingCarrier carrier);

    /** Mock carrier rate quote, used by the frontend at checkout before an order/shipment exists. */
    BigDecimal quote(ShippingCarrier carrier, int quantity);

    Shipment getByOrderId(String orderId);

    List<Shipment> list();

    void delete(String id);
}
