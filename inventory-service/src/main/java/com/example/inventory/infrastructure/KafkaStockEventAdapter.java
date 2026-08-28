package com.example.inventory.infrastructure;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.inventory.model.InventoryItem;
import com.example.inventory.ports.StockEventPort;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes {@link EventTypes#STOCK_LEVEL_CHANGED} on every mutation — unconditionally, not just
 * dips below threshold. InventoryStreamsTopology consumes this same stream and is what actually
 * derives {@link EventTypes#LOW_STOCK_DETECTED}, keyed by (productId, warehouseId), from it.
 */
@Component
@RequiredArgsConstructor
public class KafkaStockEventAdapter implements StockEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Override
    public void publishStockLevelChanged(InventoryItem item) {
        DomainEvent event = DomainEvent.of(EventTypes.STOCK_LEVEL_CHANGED, null, Map.of(
                "productId", item.getProductId(),
                "warehouseId", item.getWarehouseId(),
                "quantity", item.getQuantity()));
        kafkaTemplate.send(Topics.INVENTORY_EVENTS, event);
        meterRegistry.counter("inventory.stocklevel.changed.events").increment();
    }
}
