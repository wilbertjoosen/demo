package com.example.payment.service;
import com.example.payment.controller.PaymentGatewaySimulatorController;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Circuit-breaker-protected call to the (simulated) external payment gateway.
 *
 * <p>{@code @Retry}/{@code @RateLimiter}/{@code @Bulkhead} config lives in application.yaml under
 * {@code resilience4j.*.instances.paymentGateway} — same layering note as
 * {@link com.example.order.InventoryServiceClient} in order-service.
 */
@Component
public class PaymentGatewayClient {

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public PaymentGatewayClient(WebClient.Builder webClientBuilder,
                                 ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory,
                                 @Value("${server.port}") String serverPort) {
        this.webClient = webClientBuilder.baseUrl("http://localhost:" + serverPort).build();
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /** True if the gateway accepted the charge; false on decline OR circuit-open/timeout. */
    @Retry(name = "paymentGateway")
    @RateLimiter(name = "paymentGateway")
    @Bulkhead(name = "paymentGateway")
    public boolean charge(String orderId, int quantity) {
        Mono<Boolean> call = webClient.post()
                .uri("/internal/gateway-simulator/charge")
                .bodyValue(new PaymentGatewaySimulatorController.ChargeRequest(orderId, quantity))
                .retrieve()
                .toBodilessEntity()
                .map(response -> true)
                .onErrorReturn(false);

        ReactiveCircuitBreaker circuitBreaker = circuitBreakerFactory.create("paymentGateway");
        return Boolean.TRUE.equals(circuitBreaker.run(call, throwable -> Mono.just(false)).block());
    }
}
