package com.example.inventory.infrastructure;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.inventory.model.InventoryItem;
import com.example.inventory.ports.StockAlertPort;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes {@link EventTypes#LOW_STOCK_DETECTED} to Kafka — notification-service already relays
 * every inventory-events message to its fallback-email recipient and WebSocket broadcast, same as
 * PRODUCT_CREATED, so no new consumer is required for the "someone needs to buy more stock" alert.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaStockAlertAdapter implements StockAlertPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Override
    public void publishLowStock(InventoryItem item, int threshold) {
        DomainEvent event = DomainEvent.of(EventTypes.LOW_STOCK_DETECTED, null, Map.of(
                "productId", item.getProductId(),
                "warehouseId", item.getWarehouseId(),
                "quantity", item.getQuantity(),
                "threshold", threshold));
        kafkaTemplate.send(Topics.INVENTORY_EVENTS, event);
        meterRegistry.counter("inventory.lowstock.events").increment();
        log.warn("Low stock detected: productId={} warehouseId={} quantity={} threshold={}",
                item.getProductId(), item.getWarehouseId(), item.getQuantity(), threshold);
    }
}
