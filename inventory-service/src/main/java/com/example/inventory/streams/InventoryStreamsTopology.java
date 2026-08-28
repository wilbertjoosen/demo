package com.example.inventory.streams;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.inventory.policy.ReorderPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;

import java.util.Map;

/**
 * Derives {@code LOW_STOCK_DETECTED} from the raw {@code STOCK_LEVEL_CHANGED} stream — same
 * pattern as reporting-service's ReportingTopology (a Kafka Streams topology reacting to this
 * codebase's Kafka events), except this one also writes a derived event back out via {@code .to(...)}
 * instead of only materializing a queryable KTable.
 *
 * <p>Output goes back to {@code Topics.INVENTORY_EVENTS}, the same topic this reads from — safe
 * because the input {@code .filter} only matches {@code STOCK_LEVEL_CHANGED}, so this topology's
 * own {@code LOW_STOCK_DETECTED} output is never re-read as a new input. notification-service
 * already relays every event on this topic generically, so no new consumer wiring is needed there.
 */
@Configuration
@EnableKafkaStreams
public class InventoryStreamsTopology {

    static final String LOW_STOCK_ALERTED_STORE = "low-stock-alerted-store";

    @Bean
    public KStream<String, DomainEvent> lowStockAlertStream(
            StreamsBuilder builder, ReorderPolicy reorderPolicy, MeterRegistry meterRegistry) {
        JacksonJsonSerde<DomainEvent> eventSerde = new JacksonJsonSerde<>(DomainEvent.class).ignoreTypeHeaders();

        KStream<String, DomainEvent> alerts = builder
                .stream(Topics.INVENTORY_EVENTS, Consumed.with(Serdes.String(), eventSerde))
                .filter((k, event) -> EventTypes.STOCK_LEVEL_CHANGED.equals(event.eventType()))
                .selectKey((k, event) -> compositeKey(event))
                .processValues(new LowStockDetectionProcessorSupplier(reorderPolicy, meterRegistry));

        alerts.to(Topics.INVENTORY_EVENTS, Produced.with(Serdes.String(), eventSerde));
        return alerts;
    }

    private static String compositeKey(DomainEvent event) {
        Map<?, ?> payload = (Map<?, ?>) event.payload();
        return payload.get("productId") + "|" + payload.get("warehouseId");
    }
}
