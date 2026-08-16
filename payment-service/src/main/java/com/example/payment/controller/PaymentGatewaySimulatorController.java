package com.example.payment.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Stands in for a real external payment processor (Stripe/PayPal/etc). Kept in-process rather than
 * a separate service, purely so this demo doesn't need a real third-party dependency — but it's
 * called over real HTTP (see PaymentGatewayClient) so the circuit breaker wrapping that call behaves
 * exactly as it would against a genuine external API.
 */
@RestController
public class PaymentGatewaySimulatorController {

    /** Same demo trigger as the rest of the saga: quantity 13 simulates the gateway itself failing. */
    private static final int SIMULATED_GATEWAY_FAILURE_QUANTITY = 13;

    public record ChargeRequest(String orderId, int quantity) {
    }

    @PostMapping("/internal/gateway-simulator/charge")
    public ResponseEntity<Map<String, String>> charge(@RequestBody ChargeRequest request) {
        if (request.quantity() == SIMULATED_GATEWAY_FAILURE_QUANTITY) {
            return ResponseEntity.status(502).body(Map.of("error", "simulated gateway failure"));
        }
        return ResponseEntity.ok(Map.of("transactionId", UUID.randomUUID().toString()));
    }
}
