package com.example.shipping;

import java.util.List;

public interface ShippingService {

    /** Simulates warehouse pick + carrier handoff; publishes SHIPPED or SHIPPING_FAILED. */
    void ship(String orderId, String email, int quantity);

    List<Shipment> list();

    void delete(String id);
}
