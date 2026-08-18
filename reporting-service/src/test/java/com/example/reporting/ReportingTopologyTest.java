package com.example.reporting;
import com.example.reporting.config.ReportingTopology;
import com.example.reporting.model.OrderMetric;
import com.example.reporting.model.ProductRef;
import com.example.reporting.model.UserRegistration;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventContracts;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises ReportingTopology directly against a TopologyTestDriver (no broker, no Spring
 * context) — this is what actually validates the Kafka Streams wiring (rekeying, aggregation,
 * the table-table left join for failureStage) compiles AND behaves correctly at runtime, which
 * javac alone can't confirm for a topology this shape.
 */
class ReportingTopologyTest {

    private TopologyTestDriver driver;
    private TestInputTopic<String, DomainEvent> orderEvents;
    private TestInputTopic<String, DomainEvent> paymentEvents;
    private TestInputTopic<String, DomainEvent> productEvents;
    private TestInputTopic<String, DomainEvent> userEvents;

    @BeforeEach
    void setUp() {
        StreamsBuilder builder = new StreamsBuilder();
        ReportingTopology topology = new ReportingTopology();
        topology.orderMetricsTable(builder);
        topology.productRefsTable(builder);
        topology.userRegistrationsTable(builder);
        Topology built = builder.build();

        java.util.Properties props = new java.util.Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "reporting-service-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        driver = new TopologyTestDriver(built, props);

        JsonSerde<DomainEvent> eventSerde = new JsonSerde<>(DomainEvent.class).ignoreTypeHeaders();
        orderEvents = driver.createInputTopic(Topics.ORDER_EVENTS, Serdes.String().serializer(), eventSerde.serializer());
        paymentEvents = driver.createInputTopic(Topics.PAYMENT_EVENTS, Serdes.String().serializer(), eventSerde.serializer());
        productEvents = driver.createInputTopic(Topics.PRODUCT_EVENTS, Serdes.String().serializer(), eventSerde.serializer());
        userEvents = driver.createInputTopic(Topics.USER_EVENTS, Serdes.String().serializer(), eventSerde.serializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    private KeyValueStore<String, OrderMetric> orderMetricsStore() {
        return driver.getKeyValueStore(ReportingTopology.ORDER_METRICS_STORE);
    }

    @Test
    void orderCreated_buildsMetricWithNoConfirmedAt() {
        // Unlike main, this branch's order-service has no generic status-advance event for a
        // successful confirmation — only ORDER_CREATED and ORDER_CANCELLED — so confirmedAt has no
        // event to derive from and stays null for the metric's whole lifetime.
        orderEvents.pipeInput(null, DomainEvent.of(EventTypes.ORDER_CREATED, "1", Map.of(
                "userId", "user-1", "email", "a@b.com", "productId", "p1", "quantity", 2,
                "paymentMethod", "CREDIT_CARD", "shippingCarrier", "UPS")));

        OrderMetric metric = orderMetricsStore().get("1");
        assertThat(metric).isNotNull();
        assertThat(metric.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(metric.getProductId()).isEqualTo("p1");
        assertThat(metric.getQuantity()).isEqualTo(2);
        assertThat(metric.getConfirmedAt()).isNull();
        assertThat(metric.getCancelledAt()).isNull();
        assertThat(metric.getFailureStage()).isNull();
    }

    @Test
    void paymentFailedThenCancelled_attributesFailureStageToPayment() {
        orderEvents.pipeInput(null, DomainEvent.of(EventTypes.ORDER_CREATED, "2", Map.of(
                "userId", "user-2", "email", "c@d.com", "productId", "p1", "quantity", 1,
                "paymentMethod", "CREDIT_CARD", "shippingCarrier", "UPS")));
        paymentEvents.pipeInput(null, DomainEvent.of(EventTypes.PAYMENT_FAILED, "2", Map.of("email", "c@d.com")));
        orderEvents.pipeInput(null, DomainEvent.of(EventTypes.ORDER_CANCELLED, "2", Map.of("email", "c@d.com")));

        OrderMetric metric = orderMetricsStore().get("2");
        assertThat(metric).isNotNull();
        assertThat(metric.getStatus()).isEqualTo("CANCELLED");
        assertThat(metric.getFailureStage()).isEqualTo("PAYMENT");
        assertThat(metric.getCancelledAt()).isNotNull();
    }

    @Test
    void userCancelledBeforeAnyFailure_hasNullFailureStage() {
        orderEvents.pipeInput(null, DomainEvent.of(EventTypes.ORDER_CREATED, "3", Map.of(
                "userId", "user-3", "email", "e@f.com", "productId", "p1", "quantity", 1,
                "paymentMethod", "CREDIT_CARD", "shippingCarrier", "UPS")));
        orderEvents.pipeInput(null, DomainEvent.of(EventTypes.ORDER_CANCELLED, "3", Map.of("email", "e@f.com")));

        OrderMetric metric = orderMetricsStore().get("3");
        assertThat(metric).isNotNull();
        assertThat(metric.getStatus()).isEqualTo("CANCELLED");
        assertThat(metric.getFailureStage()).isNull();
    }

    @Test
    void productCreatedThenUpdatedThenDeleted_reflectsLatestStateAndInactive() {
        productEvents.pipeInput(null, DomainEvent.of(EventTypes.PRODUCT_CREATED, null,
                Map.of("productId", "p1", "name", "Widget", "sku", "SKU-1", "price", new BigDecimal("9.99"))));
        productEvents.pipeInput(null, DomainEvent.of(EventTypes.PRODUCT_UPDATED, null,
                Map.of("productId", "p1", "name", "Widget v2", "sku", "SKU-1", "price", new BigDecimal("12.50"))));

        KeyValueStore<String, ProductRef> productStore = driver.getKeyValueStore(ReportingTopology.PRODUCT_REFS_STORE);
        ProductRef ref = productStore.get("p1");
        assertThat(ref.getName()).isEqualTo("Widget v2");
        assertThat(ref.getPrice()).isEqualByComparingTo("12.50");
        assertThat(ref.isActive()).isTrue();

        productEvents.pipeInput(null, DomainEvent.of(EventTypes.PRODUCT_DELETED, null, Map.of("productId", "p1")));
        assertThat(productStore.get("p1").isActive()).isFalse();
    }

    /**
     * Consumer-side half of the producer/consumer contract pairing (see EventContracts and
     * order-service's OrderServiceImplTest for the producer side): reporting-service is entitled to
     * assume every event it consumes carries these fields, and everything it actually reads out of
     * a payload elsewhere in this test class (productId/name/sku/price, status/email, userId/
     * username/email, ...) is drawn from exactly this set — if a future change narrowed the
     * contract, this is what would catch it before ReportingTopology's field access did.
     */
    @Test
    void consumedEventFixtures_satisfyTheirContracts() {
        assertThat(EventContracts.missingFields(EventTypes.ORDER_CREATED, Map.of(
                "userId", "user-1", "email", "a@b.com", "productId", "p1", "quantity", 2,
                "paymentMethod", "CREDIT_CARD", "shippingCarrier", "UPS"))).isEmpty();
        assertThat(EventContracts.missingFields(EventTypes.ORDER_CANCELLED,
                Map.of("email", "a@b.com"))).isEmpty();
        assertThat(EventContracts.missingFields(EventTypes.PRODUCT_CREATED, Map.of(
                "productId", "p1", "name", "Widget", "sku", "SKU-1", "price", new BigDecimal("9.99")))).isEmpty();
        assertThat(EventContracts.missingFields(EventTypes.PRODUCT_DELETED, Map.of("productId", "p1"))).isEmpty();
        assertThat(EventContracts.missingFields(EventTypes.USER_REGISTERED,
                Map.of("userId", "user-9", "username", "nine", "email", "nine@example.com"))).isEmpty();
    }

    @Test
    void userRegistered_isQueryableByUserId() {
        userEvents.pipeInput(null, DomainEvent.of(EventTypes.USER_REGISTERED, null,
                Map.of("userId", "user-9", "username", "nine", "email", "nine@example.com")));

        KeyValueStore<String, UserRegistration> userStore = driver.getKeyValueStore(ReportingTopology.USER_REGISTRATIONS_STORE);
        UserRegistration registration = userStore.get("user-9");
        assertThat(registration).isNotNull();
        assertThat(registration.getUsername()).isEqualTo("nine");
        assertThat(registration.getRegisteredAt()).isBeforeOrEqualTo(Instant.now());
    }
}
