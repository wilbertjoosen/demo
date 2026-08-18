package com.example.reporting.config;
import com.example.reporting.model.OrderMetric;
import com.example.reporting.model.ProductRef;
import com.example.reporting.model.UserRegistration;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.util.Map;
import java.util.Set;

/**
 * Builds the entire reporting read model as Kafka Streams KTables — state lives in RocksDB
 * (backed by a Kafka changelog topic per store) instead of a hand-maintained Mongo collection.
 * Every KTable here is queried later via Spring Kafka's InteractiveQueryService (see
 * ReportsServiceImpl), not written to directly by any REST call.
 */
@Configuration
@EnableKafkaStreams
public class ReportingTopology {

    public static final String ORDER_METRICS_STORE = "order-metrics-store";
    public static final String PRODUCT_REFS_STORE = "product-refs-store";
    public static final String USER_REGISTRATIONS_STORE = "user-registrations-store";

    private static final Set<String> ORDER_EVENT_TYPES = Set.of(EventTypes.ORDER_CREATED, EventTypes.ORDER_STATUS_CHANGED);
    private static final Set<String> PRODUCT_EVENT_TYPES =
            Set.of(EventTypes.PRODUCT_CREATED, EventTypes.PRODUCT_UPDATED, EventTypes.PRODUCT_DELETED);

    private JsonSerde<DomainEvent> eventSerde() {
        return new JsonSerde<>(DomainEvent.class).ignoreTypeHeaders();
    }

    @Bean
    public KTable<String, OrderMetric> orderMetricsTable(StreamsBuilder builder) {
        JsonSerde<OrderMetric> orderMetricSerde = new JsonSerde<>(OrderMetric.class).ignoreTypeHeaders();

        KTable<String, OrderMetric> baseMetrics = builder
                .stream(Topics.ORDER_EVENTS, Consumed.with(Serdes.String(), eventSerde()))
                .filter((k, event) -> ORDER_EVENT_TYPES.contains(event.eventType()))
                .selectKey((k, event) -> event.orderId())
                .groupByKey(Grouped.with(Serdes.String(), eventSerde()))
                .aggregate(OrderMetric::new, (orderId, event, metric) -> metric.apply(event),
                        Materialized.<String, OrderMetric, KeyValueStore<Bytes, byte[]>>as("order-metrics-base-store")
                                .withKeySerde(Serdes.String()).withValueSerde(orderMetricSerde));

        KTable<String, String> failureStageTable = failureStageStream(builder, Topics.PAYMENT_EVENTS, EventTypes.PAYMENT_FAILED, "PAYMENT")
                .merge(failureStageStream(builder, Topics.SHIPPING_EVENTS, EventTypes.SHIPPING_FAILED, "SHIPPING"))
                .merge(failureStageStream(builder, Topics.DELIVERY_EVENTS, EventTypes.DELIVERY_FAILED, "DELIVERY"))
                .toTable(Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as("failure-stage-store")
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.String()));

        return baseMetrics.leftJoin(failureStageTable, OrderMetric::withFailureStage,
                Materialized.<String, OrderMetric, KeyValueStore<Bytes, byte[]>>as(ORDER_METRICS_STORE)
                        .withKeySerde(Serdes.String()).withValueSerde(orderMetricSerde));
    }

    private KStream<String, String> failureStageStream(StreamsBuilder builder, String topic, String failureEventType, String stage) {
        return builder.stream(topic, Consumed.with(Serdes.String(), eventSerde()))
                .filter((k, event) -> failureEventType.equals(event.eventType()))
                .map((k, event) -> KeyValue.pair(event.orderId(), stage));
    }

    @Bean
    public KTable<String, ProductRef> productRefsTable(StreamsBuilder builder) {
        JsonSerde<ProductRef> productRefSerde = new JsonSerde<>(ProductRef.class).ignoreTypeHeaders();

        return builder.stream(Topics.PRODUCT_EVENTS, Consumed.with(Serdes.String(), eventSerde()))
                .filter((k, event) -> PRODUCT_EVENT_TYPES.contains(event.eventType()))
                .map((k, event) -> KeyValue.pair(productIdOf(event), event))
                .groupByKey(Grouped.with(Serdes.String(), eventSerde()))
                .aggregate(ProductRef::new, (productId, event, ref) -> ref.apply(event),
                        Materialized.<String, ProductRef, KeyValueStore<Bytes, byte[]>>as(PRODUCT_REFS_STORE)
                                .withKeySerde(Serdes.String()).withValueSerde(productRefSerde));
    }

    @Bean
    public KTable<String, UserRegistration> userRegistrationsTable(StreamsBuilder builder) {
        JsonSerde<UserRegistration> userRegistrationSerde = new JsonSerde<>(UserRegistration.class).ignoreTypeHeaders();

        return builder.stream(Topics.USER_EVENTS, Consumed.with(Serdes.String(), eventSerde()))
                .filter((k, event) -> EventTypes.USER_REGISTERED.equals(event.eventType()))
                .map((k, event) -> {
                    Map<?, ?> payload = (Map<?, ?>) event.payload();
                    String userId = (String) payload.get("userId");
                    return KeyValue.pair(userId, new UserRegistration(
                            userId, (String) payload.get("username"), (String) payload.get("email"), event.timestamp()));
                })
                .toTable(Materialized.<String, UserRegistration, KeyValueStore<Bytes, byte[]>>as(USER_REGISTRATIONS_STORE)
                        .withKeySerde(Serdes.String()).withValueSerde(userRegistrationSerde));
    }

    private String productIdOf(DomainEvent event) {
        return (String) ((Map<?, ?>) event.payload()).get("productId");
    }
}
