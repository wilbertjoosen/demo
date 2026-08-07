package com.example.delivery;

import java.util.List;

public interface DeliveryService {

    /** Simulates last-mile delivery; publishes DELIVERED or DELIVERY_FAILED. */
    void deliver(String orderId, String email, int quantity);

    List<Delivery> list();

    void delete(String id);
}
