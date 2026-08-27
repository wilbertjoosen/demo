package com.example.order;
import com.example.order.saga.OrderSagaListener;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.order.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.apache.kafka.clients.admin.NewTopic;

import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * order-service's side of the choreographed saga, exercised through the REAL Kafka consumer
 * ({@link OrderSagaListener}'s {@code @KafkaListener} methods) rather than calling it directly —
 * OrderSagaListenerTest (unit) already pins the event-to-action mapping; this pins that the topic
 * names, consumer group, and JSON deserialization actually wire up end to end.
 */
@Import(OrderSagaIntegrationTest.PreCreatedTopics.class)
class OrderSagaIntegrationTest extends AbstractIntegrationTest {

    /**
     * order-service never produces to payment/shipping/delivery-events (only consumes), so unlike
     * order-events, these topics don't exist yet when this test's Spring context starts. Without
     * pre-creating them, the consumer's very first metadata fetch finds no such topic; Kafka
     * auto-creates it on first publish, but the already-running consumer can take a long time to
     * notice — pre-creating via NewTopic beans (auto-applied by Spring Kafka's KafkaAdmin at context
     * startup) avoids that race entirely.
     */
    @TestConfiguration
    static class PreCreatedTopics {
        @Bean
        NewTopic paymentEventsTopic() {
            return TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic shippingEventsTopic() {
            return TopicBuilder.name(Topics.SHIPPING_EVENTS).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic deliveryEventsTopic() {
            return TopicBuilder.name(Topics.DELIVERY_EVENTS).partitions(1).replicas(1).build();
        }
    }

    static WireMockServer inventoryService;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    // The app's own bean (JsonSerializer, same as production) — test events are published with the
    // exact same serialization order-service itself uses when it publishes ORDER_CREATED.
    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeAll
    static void startWireMock() {
        inventoryService = new WireMockServer(0);
        inventoryService.start();
    }

    @AfterAll
    static void stopWireMock() {
        inventoryService.stop();
    }

    @DynamicPropertySource
    static void inventoryServiceUri(DynamicPropertyRegistry registry) {
        registry.add("services.inventory-service.uri", () -> "http://localhost:" + inventoryService.port());
    }

    @BeforeEach
    void resetStubs() {
        inventoryService.resetAll();
        inventoryService.stubFor(WireMock.post(urlPathMatching("/api/inventory/.*")).willReturn(aResponse().withStatus(200)));
    }

    private static RequestPostProcessor userToken(String subject) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(subject).claim("email", subject + "@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Long placeOrder(String subject) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .with(userToken(subject))
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "product-1",
                                  "quantity": 1,
                                  "shippingAddress": {"street":"1 Main St","city":"Springfield","postalCode":"12345","country":"US"},
                                  "paymentMethod": "CREDIT_CARD",
                                  "shippingCarrier": "UPS"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(objectMapper.readTree(body).get("id").asText());
    }

    private void assertEventuallyStatus(String subject, Long orderId, String expectedStatus) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mockMvc.perform(get("/api/orders/{id}", orderId).with(userToken(subject)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value(expectedStatus)));
    }

    @Test
    void paymentCompleted_advancesOrderToPaid() throws Exception {
        Long orderId = placeOrder("saga-user-1");

        kafkaTemplate.send(Topics.PAYMENT_EVENTS, DomainEvent.of(EventTypes.PAYMENT_COMPLETED, orderId.toString(), Map.of()));

        assertEventuallyStatus("saga-user-1", orderId, "PAID");
    }

    @Test
    void paymentFailed_cancelsOrderAndReleasesStock() throws Exception {
        Long orderId = placeOrder("saga-user-2");

        kafkaTemplate.send(Topics.PAYMENT_EVENTS, DomainEvent.of(EventTypes.PAYMENT_FAILED, orderId.toString(), Map.of()));

        assertEventuallyStatus("saga-user-2", orderId, "CANCELLED");
        inventoryService.verify(1, postRequestedFor(urlPathMatching("/api/inventory/product-1/release")));
    }

    @Test
    void fullHappyPathSaga_paidThenShippedThenConfirmed() throws Exception {
        Long orderId = placeOrder("saga-user-3");

        kafkaTemplate.send(Topics.PAYMENT_EVENTS, DomainEvent.of(EventTypes.PAYMENT_COMPLETED, orderId.toString(), Map.of()));
        assertEventuallyStatus("saga-user-3", orderId, "PAID");

        kafkaTemplate.send(Topics.SHIPPING_EVENTS, DomainEvent.of(EventTypes.SHIPPED, orderId.toString(), Map.of()));
        assertEventuallyStatus("saga-user-3", orderId, "SHIPPED");

        kafkaTemplate.send(Topics.DELIVERY_EVENTS, DomainEvent.of(EventTypes.DELIVERED, orderId.toString(), Map.of()));
        assertEventuallyStatus("saga-user-3", orderId, "CONFIRMED");
    }

    @Test
    void paymentRefunded_cancelsOrder_compensationAfterShippingFailure() throws Exception {
        // order-service doesn't react to SHIPPING_FAILED directly (payment-service issues the refund;
        // order-service only reacts to the resulting PAYMENT_REFUNDED) — simulate that final event here.
        Long orderId = placeOrder("saga-user-4");
        kafkaTemplate.send(Topics.PAYMENT_EVENTS, DomainEvent.of(EventTypes.PAYMENT_COMPLETED, orderId.toString(), Map.of()));
        assertEventuallyStatus("saga-user-4", orderId, "PAID");

        kafkaTemplate.send(Topics.PAYMENT_EVENTS, DomainEvent.of(EventTypes.PAYMENT_REFUNDED, orderId.toString(), Map.of()));

        assertEventuallyStatus("saga-user-4", orderId, "CANCELLED");
    }
}
