package com.example.common.events;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The payload fields every producer of a given event type actually guarantees, and every consumer
 * is therefore entitled to rely on — the lightweight, no-schema-registry stand-in for a real schema
 * contract (see the architecture review's "Kafka events have no schema/contract" finding). Fields
 * listed here were audited against every real {@code DomainEvent.of(...)} call site in the codebase
 * at the time this was written; a producer test asserting {@link #missingFields} is empty is what
 * keeps this registry honest as the code evolves, the same way ProductRef/OrderMetric's construction
 * in reporting-service depends on these exact fields existing.
 *
 * <p>Deliberately just a {@code Map<String, Set<String>>} plus a pure-java.util checker — no JUnit/
 * AssertJ dependency, so this can live in common-security's main sourceset (used by both production
 * and test code) without pulling a test framework into every consuming service's runtime classpath.
 */
public final class EventContracts {

    private static final Map<String, Set<String>> REQUIRED_FIELDS = Map.ofEntries(
            Map.entry(EventTypes.USER_REGISTERED, Set.of("userId", "username", "email")),
            Map.entry(EventTypes.PRODUCT_CREATED, Set.of("productId", "name", "sku", "price")),
            Map.entry(EventTypes.PRODUCT_UPDATED, Set.of("productId", "name", "sku", "price")),
            Map.entry(EventTypes.PRODUCT_DELETED, Set.of("productId")),
            Map.entry(EventTypes.ORDER_CREATED,
                    Set.of("userId", "email", "productId", "quantity", "paymentMethod", "shippingCarrier")),
            Map.entry(EventTypes.ORDER_STATUS_CHANGED, Set.of("status", "email")),
            Map.entry(EventTypes.PAYMENT_COMPLETED, Set.of("paymentId", "email", "quantity", "userId", "shippingCarrier")),
            // Only the fields guaranteed by EVERY PAYMENT_FAILED publish site — payment-service has
            // two (gateway-unavailable vs a declined charge) and they don't carry identical extra
            // fields ("reason" vs "userId"/"shippingCarrier"), so the contract is their intersection,
            // not the union of what either happens to include.
            Map.entry(EventTypes.PAYMENT_FAILED, Set.of("paymentId", "email", "quantity")),
            Map.entry(EventTypes.PAYMENT_REFUNDED, Set.of("paymentId", "email")),
            Map.entry(EventTypes.PAYMENT_INSTRUCTIONS_REQUIRED, Set.of("paymentId", "email", "method")),
            Map.entry(EventTypes.SHIPPED, Set.of("email", "quantity")),
            Map.entry(EventTypes.SHIPPING_FAILED, Set.of("email", "quantity")),
            Map.entry(EventTypes.DELIVERED, Set.of("email", "quantity")),
            Map.entry(EventTypes.DELIVERY_FAILED, Set.of("email", "quantity")),
            Map.entry(EventTypes.STOCK_LEVEL_CHANGED, Set.of("productId", "warehouseId", "quantity")),
            Map.entry(EventTypes.LOW_STOCK_DETECTED, Set.of("productId", "warehouseId", "quantity", "threshold")));

    public static Set<String> requiredFields(String eventType) {
        Set<String> fields = REQUIRED_FIELDS.get(eventType);
        if (fields == null) {
            throw new IllegalArgumentException("No contract registered for event type: " + eventType);
        }
        return fields;
    }

    /** Empty when {@code payload} satisfies the contract; otherwise the required keys that are missing or null. */
    public static List<String> missingFields(String eventType, Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                    "Payload for " + eventType + " must be a Map to check against a contract, was: "
                            + (payload == null ? "null" : payload.getClass()));
        }
        return requiredFields(eventType).stream()
                .filter(field -> map.get(field) == null)
                .toList();
    }

    private EventContracts() {
    }
}
