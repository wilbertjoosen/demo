package com.example.inventory.streams;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.inventory.policy.ReorderPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Edge-detects a warehouse line dipping below {@link ReorderPolicy}'s threshold from the raw
 * {@code STOCK_LEVEL_CHANGED} stream, keyed by "productId|warehouseId" (see
 * {@link InventoryStreamsTopology#compositeKey}). {@code alertedStore} replaces the CAS-guarded
 * {@code lowStockAlerted} Mongo field the synchronous version of this used to keep — same
 * once-per-dip semantics, now living in this topology's own state store instead of the document,
 * so there's one place tracking "is this line currently in alert", not two that could drift.
 */
class LowStockDetectionProcessor implements FixedKeyProcessor<String, DomainEvent, DomainEvent> {

    private static final Logger log = LoggerFactory.getLogger(LowStockDetectionProcessor.class);

    private final ReorderPolicy reorderPolicy;
    private final MeterRegistry meterRegistry;
    private FixedKeyProcessorContext<String, DomainEvent> context;
    private KeyValueStore<String, Boolean> alertedStore;

    LowStockDetectionProcessor(ReorderPolicy reorderPolicy, MeterRegistry meterRegistry) {
        this.reorderPolicy = reorderPolicy;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void init(FixedKeyProcessorContext<String, DomainEvent> context) {
        this.context = context;
        this.alertedStore = context.getStateStore(InventoryStreamsTopology.LOW_STOCK_ALERTED_STORE);
    }

    @Override
    public void process(FixedKeyRecord<String, DomainEvent> record) {
        Map<?, ?> payload = (Map<?, ?>) record.value().payload();
        int quantity = ((Number) payload.get("quantity")).intValue();
        String key = record.key();
        boolean alreadyAlerted = Boolean.TRUE.equals(alertedStore.get(key));
        boolean isLow = reorderPolicy.isLowStock(quantity);

        if (isLow && !alreadyAlerted) {
            alertedStore.put(key, true);
            context.forward(record.withValue(lowStockEvent(payload, quantity)));
            meterRegistry.counter("inventory.lowstock.events").increment();
            log.warn("Low stock detected: productId={} warehouseId={} quantity={} threshold={}",
                    payload.get("productId"), payload.get("warehouseId"), quantity, reorderPolicy.threshold());
        } else if (!isLow && alreadyAlerted) {
            alertedStore.put(key, false);
        }
    }

    private DomainEvent lowStockEvent(Map<?, ?> payload, int quantity) {
        return DomainEvent.of(EventTypes.LOW_STOCK_DETECTED, null, Map.of(
                "productId", payload.get("productId"),
                "warehouseId", payload.get("warehouseId"),
                "quantity", quantity,
                "threshold", reorderPolicy.threshold()));
    }
}
