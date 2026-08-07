package com.example.payment;

import com.example.common.model.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for periodically polling each external payment processor's status/health endpoint
 * (Stripe status page, PayPal health check, a PIX/boleto processor's ping, etc). Every real
 * gateway integration needs exactly this: know before attempting a charge whether the method is
 * even worth offering, rather than letting the customer pick a method that's certain to fail.
 *
 * <p>Availability here is a simple periodic flap simulation rather than a real HTTP health check
 * (there's nothing external to actually poll in this demo) — {@link #charge}/{@link PaymentServiceImpl}
 * treat this the same way they'd treat a real health-check result. An admin can also force a
 * method down/up via {@link #setAvailable} to demonstrate the "gateway is down" path on demand.
 */
@Component
@Slf4j
public class PaymentGatewayAvailability {

    /** Each method flaps down with roughly this probability per tick — small enough that most demo runs stay green. */
    private static final double DOWN_PROBABILITY = 0.05;

    private final Map<PaymentMethod, Boolean> availability = new EnumMap<>(PaymentMethod.class);
    private final Map<PaymentMethod, Boolean> manualOverride = new EnumMap<>(PaymentMethod.class);

    public PaymentGatewayAvailability() {
        for (PaymentMethod method : PaymentMethod.values()) {
            availability.put(method, true);
        }
    }

    public boolean isAvailable(PaymentMethod method) {
        return availability.getOrDefault(method, true);
    }

    public Map<PaymentMethod, Boolean> snapshot() {
        return new EnumMap<>(availability);
    }

    /** Admin override — pins a method up/down regardless of the simulated flap, until cleared. */
    public void setAvailable(PaymentMethod method, boolean available) {
        manualOverride.put(method, available);
        availability.put(method, available);
        log.info("Payment gateway for {} manually set to {}", method, available ? "AVAILABLE" : "UNAVAILABLE");
    }

    public void clearOverride(PaymentMethod method) {
        manualOverride.remove(method);
    }

    @Scheduled(fixedDelay = 30_000)
    void simulateHealthCheck() {
        for (PaymentMethod method : PaymentMethod.values()) {
            if (manualOverride.containsKey(method)) {
                continue;
            }
            boolean wasAvailable = availability.get(method);
            boolean nowAvailable = ThreadLocalRandom.current().nextDouble() >= DOWN_PROBABILITY;
            availability.put(method, nowAvailable);
            if (wasAvailable != nowAvailable) {
                log.warn("Payment gateway for {} is now {}", method, nowAvailable ? "AVAILABLE" : "UNAVAILABLE");
            }
        }
    }
}
