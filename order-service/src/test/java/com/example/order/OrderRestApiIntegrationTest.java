package com.example.order;
import com.example.order.config.Resilience4jConfig;

import com.example.order.support.AbstractIntegrationTest;
import com.example.order.support.TestOAuth2ClientConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST + persistence (real MySQL write model, real Mongo read model) + the one real synchronous
 * call this system makes, to a WireMock-stubbed inventory-service — exercising the actual
 * Resilience4j circuit breaker/retry config from Resilience4jConfig/application.yaml, not a mock
 * of it.
 */
class OrderRestApiIntegrationTest extends AbstractIntegrationTest {

    static WireMockServer inventoryService;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

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
    }

    private static RequestPostProcessor userToken(String subject) {
        return userToken(subject, jwt -> { });
    }

    private static RequestPostProcessor userToken(String subject, Consumer<Jwt.Builder> customizer) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> {
                    jwt.subject(subject);
                    customizer.accept(jwt);
                })
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static String placeOrderJson() {
        return """
                {
                  "productId": "product-1",
                  "quantity": 2,
                  "shippingAddress": {"street":"1 Main St","city":"Springfield","postalCode":"12345","country":"US"},
                  "paymentMethod": "CREDIT_CARD",
                  "shippingCarrier": "UPS"
                }
                """;
    }

    @Test
    void placeOrder_reservesStockAndPersistsInBothModels() throws Exception {
        inventoryService.stubFor(WireMock.post(urlPathMatching("/api/inventory/product-1/reserve"))
                .willReturn(aResponse().withStatus(200)));

        mockMvc.perform(post("/api/orders")
                        .with(userToken("user-1", jwt -> jwt.claim("email", "buyer@example.com")))
                        .contentType("application/json")
                        .content(placeOrderJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.productId").value("product-1"));

        inventoryService.verify(1, postRequestedFor(urlPathMatching("/api/inventory/product-1/reserve"))
                .withHeader("Authorization",
                        com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer " + TestOAuth2ClientConfig.FAKE_TOKEN)));

        mockMvc.perform(get("/api/orders").with(userToken("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.orderViewList", hasSize(1)));
    }

    @Test
    void placeOrder_insufficientStock_returns409AndPersistsNothing() throws Exception {
        inventoryService.stubFor(WireMock.post(urlPathMatching("/api/inventory/product-1/reserve"))
                .willReturn(aResponse().withStatus(409)));

        mockMvc.perform(post("/api/orders")
                        .with(userToken("user-2", jwt -> jwt.claim("email", "buyer2@example.com")))
                        .contentType("application/json")
                        .content(placeOrderJson()))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/orders").with(userToken("user-2")))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$._embedded").doesNotExist());
    }

    @Test
    void cancelOrder_releasesStockViaInventoryService() throws Exception {
        inventoryService.stubFor(WireMock.post(urlPathMatching("/api/inventory/product-1/reserve"))
                .willReturn(aResponse().withStatus(200)));
        inventoryService.stubFor(WireMock.post(urlPathMatching("/api/inventory/product-1/release"))
                .willReturn(aResponse().withStatus(200)));

        String body = mockMvc.perform(post("/api/orders")
                        .with(userToken("user-3", jwt -> jwt.claim("email", "buyer3@example.com")))
                        .contentType("application/json")
                        .content(placeOrderJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(userToken("user-3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        inventoryService.verify(1, postRequestedFor(urlPathMatching("/api/inventory/product-1/release")));
    }

    @Test
    void circuitBreaker_opensAfterRepeatedInventoryServiceFailures() throws Exception {
        // Resilience4jConfig: slidingWindowSize=4, minimumNumberOfCalls=4, failureRateThreshold=50% —
        // 4 failing calls should be enough to trip the breaker open; a 5th call should fail WITHOUT
        // ever reaching WireMock (fails fast from the open breaker instead).
        inventoryService.stubFor(WireMock.post(urlPathMatching("/api/inventory/product-1/reserve"))
                .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/orders")
                            .with(userToken("user-cb", jwt -> jwt.claim("email", "cb@example.com")))
                            .contentType("application/json")
                            .content(placeOrderJson()))
                    .andExpect(status().isConflict());
        }

        int callsBeforeOpen = inventoryService.countRequestsMatching(
                        postRequestedFor(urlPathMatching("/api/inventory/product-1/reserve")).build())
                .getCount();

        mockMvc.perform(post("/api/orders")
                        .with(userToken("user-cb"))
                        .contentType("application/json")
                        .content(placeOrderJson()))
                .andExpect(status().isConflict());

        int callsAfterOpen = inventoryService.countRequestsMatching(
                        postRequestedFor(urlPathMatching("/api/inventory/product-1/reserve")).build())
                .getCount();

        Assertions.assertThat(callsAfterOpen)
                .as("open breaker should fail fast, never reaching WireMock for the 5th call")
                .isEqualTo(callsBeforeOpen);
    }
}
