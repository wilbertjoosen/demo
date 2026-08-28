package com.example.inventory.streams;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventContracts;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.inventory.policy.FixedThresholdReorderPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises InventoryStreamsTopology directly against a TopologyTestDriver (no broker, no Spring
 * context) — same approach as reporting-service's ReportingTopologyTest. This is what actually
 * validates the edge-trigger behavior (fires once per dip, not once per update; resets on
 * recovery; independent per warehouse line) — javac alone can't confirm a stateful Processor
 * behaves correctly at runtime.
 */
class InventoryStreamsTopologyTest {

    private static final int THRESHOLD = 10;

    private TopologyTestDriver driver;
    private TestInputTopic<String, DomainEvent> inventoryEventsIn;
    private TestOutputTopic<String, DomainEvent> inventoryEventsOut;

    @BeforeEach
    void setUp() {
        StreamsBuilder builder = new StreamsBuilder();
        new InventoryStreamsTopology().lowStockAlertStream(
                builder, new FixedThresholdReorderPolicy(THRESHOLD), new SimpleMeterRegistry());
        Topology built = builder.build();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "inventory-service-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        driver = new TopologyTestDriver(built, props);

        JsonSerde<DomainEvent> eventSerde = new JsonSerde<>(DomainEvent.class).ignoreTypeHeaders();
        inventoryEventsIn = driver.createInputTopic(Topics.INVENTORY_EVENTS, Serdes.String().serializer(), eventSerde.serializer());
        inventoryEventsOut = driver.createOutputTopic(Topics.INVENTORY_EVENTS, Serdes.String().deserializer(), eventSerde.deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    private static DomainEvent stockLevelChanged(String productId, String warehouseId, int quantity) {
        return DomainEvent.of(EventTypes.STOCK_LEVEL_CHANGED, null,
                Map.of("productId", productId, "warehouseId", warehouseId, "quantity", quantity));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(DomainEvent event) {
        return (Map<String, Object>) event.payload();
    }

    @Test
    void dipBelowThreshold_emitsLowStockDetectedOnceEvenIfStillLowNextUpdate() {
        inventoryEventsIn.pipeInput(null, stockLevelChanged("p1", "MAIN", 15));
        inventoryEventsIn.pipeInput(null, stockLevelChanged("p1", "MAIN", 8));
        inventoryEventsIn.pipeInput(null, stockLevelChanged("p1", "MAIN", 5));

        DomainEvent alert = inventoryEventsOut.readValue();
        assertThat(alert.eventType()).isEqualTo(EventTypes.LOW_STOCK_DETECTED);
        Map<String, Object> payload = payloadOf(alert);
        assertThat(payload).containsEntry("productId", "p1").containsEntry("warehouseId", "MAIN")
                .containsEntry("quantity", 8).containsEntry("threshold", THRESHOLD);
        assertThat(EventContracts.missingFields(EventTypes.LOW_STOCK_DETECTED, payload)).isEmpty();
        assertThat(inventoryEventsOut.isEmpty()).isTrue();
    }

    @Test
    void neverDipsBelowThreshold_emitsNothing() {
        inventoryEventsIn.pipeInput(null, stockLevelChanged("p2", "MAIN", 50));
        inventoryEventsIn.pipeInput(null, stockLevelChanged("p2", "MAIN", 20));

        assertThat(inventoryEventsOut.isEmpty()).isTrue();
    }

    @Test
    void recoversAboveThresholdThenDipsAgain_firesASecondAlert() {
        inventoryEventsIn.pipeInput(null, stockLevelChanged("p3", "MAIN", 5));
        inventoryEventsIn.pipeInput(null, stockLevelChanged("p3", "MAIN", 50));
        inventoryEventsIn.pipeInput(null, stockLevelChanged("p3", "MAIN", 3));

        assertThat(payloadOf(inventoryEventsOut.readValue())).containsEntry("quantity", 5);
        assertThat(payloadOf(inventoryEventsOut.readValue())).containsEntry("quantity", 3);
        assertThat(inventoryEventsOut.isEmpty()).isTrue();
    }

    @Test
    void differentWarehouseLinesForSameProduct_trackedIndependently() {
        inventoryEventsIn.pipeInput(null, stockLevelChanged("p4", "MAIN", 5));
        inventoryEventsIn.pipeInput(null, stockLevelChanged("p4", "EAST", 5));

        assertThat(payloadOf(inventoryEventsOut.readValue())).containsEntry("warehouseId", "MAIN");
        assertThat(payloadOf(inventoryEventsOut.readValue())).containsEntry("warehouseId", "EAST");
        assertThat(inventoryEventsOut.isEmpty()).isTrue();
    }
}
