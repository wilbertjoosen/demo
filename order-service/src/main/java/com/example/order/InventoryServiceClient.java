package com.example.order;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * The only synchronous inter-service call in the system — everything else in the saga is async
 * via Kafka. That's exactly why this one call is wrapped in a circuit breaker: if inventory-service
 * is down, this fails fast with a fallback instead of order-service hanging/timing out.
 * Authenticated via client_credentials (see InventoryClientConfig), not by relaying the user's token.
 *
 * <p>{@code @Retry}/{@code @RateLimiter}/{@code @Bulkhead} wrap the whole method (config in
 * application.yaml, {@code resilience4j.*.instances.inventoryService}) — outside the programmatic
 * circuit breaker below, so a retry re-attempts the full circuit-breaker-guarded call each time.
 * {@code TimeLimiter} is already covered by {@link Resilience4jConfig}'s reactive config.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceClient {

    private final WebClient inventoryServiceWebClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    /** True if stock was reserved; false on business rejection (insufficient stock) OR circuit-open/downstream failure. */
    @Retry(name = "inventoryService")
    @RateLimiter(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public boolean reserve(String productId, int quantity) {
        Mono<Boolean> call = inventoryServiceWebClient.post()
                .uri("/api/inventory/{id}/reserve", productId)
                .headers(h -> h.setBearerAuth(fetchAccessToken()))
                .bodyValue(Map.of("quantity", quantity))
                .retrieve()
                .toBodilessEntity()
                .map(response -> true);

        ReactiveCircuitBreaker circuitBreaker = circuitBreakerFactory.create("inventoryService");
        return Boolean.TRUE.equals(circuitBreaker.run(call, throwable -> {
            log.warn("inventory-service reserve failed for product {} (quantity {}): {}", productId, quantity, throwable.toString());
            return Mono.just(false);
        }).block());
    }

    @Retry(name = "inventoryService")
    @RateLimiter(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public void release(String productId, int quantity) {
        Mono<Void> call = inventoryServiceWebClient.post()
                .uri("/api/inventory/{id}/release", productId)
                .headers(h -> h.setBearerAuth(fetchAccessToken()))
                .bodyValue(Map.of("quantity", quantity))
                .retrieve()
                .toBodilessEntity()
                .then();

        ReactiveCircuitBreaker circuitBreaker = circuitBreakerFactory.create("inventoryService");
        circuitBreaker.run(call, throwable -> {
            log.error("inventory-service release FAILED for product {} (quantity {}) — stock is now inconsistent: {}",
                    productId, quantity, throwable.toString());
            return Mono.empty();
        }).block();
    }

    private String fetchAccessToken() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId("inventory-service")
                .principal("order-service")
                .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(request);
        if (authorizedClient == null) {
            throw new IllegalStateException("Could not authorize client_credentials for inventory-service");
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }
}
