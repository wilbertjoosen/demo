package com.example.inventory.streams;

import com.example.common.events.DomainEvent;
import com.example.inventory.policy.ReorderPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorSupplier;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

import java.util.Set;

class LowStockDetectionProcessorSupplier implements FixedKeyProcessorSupplier<String, DomainEvent, DomainEvent> {

    private final ReorderPolicy reorderPolicy;
    private final MeterRegistry meterRegistry;

    LowStockDetectionProcessorSupplier(ReorderPolicy reorderPolicy, MeterRegistry meterRegistry) {
        this.reorderPolicy = reorderPolicy;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public FixedKeyProcessor<String, DomainEvent, DomainEvent> get() {
        return new LowStockDetectionProcessor(reorderPolicy, meterRegistry);
    }

    @Override
    public Set<StoreBuilder<?>> stores() {
        return Set.of(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(InventoryStreamsTopology.LOW_STOCK_ALERTED_STORE),
                Serdes.String(), Serdes.Boolean()));
    }
}
