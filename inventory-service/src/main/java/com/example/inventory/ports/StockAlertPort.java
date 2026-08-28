package com.example.inventory.ports;

import com.example.inventory.model.InventoryItem;

/** Outbound port for "this line needs restocking" alerts — keeps the service layer unaware of Kafka. */
public interface StockAlertPort {

    void publishLowStock(InventoryItem item, int threshold);
}
