package com.example.common.events;

public final class EventTypes {

    public static final String USER_REGISTERED = "USER_REGISTERED";

    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_DELETED = "PRODUCT_DELETED";

    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";

    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";
    /** BANK_TRANSFER/CASH only — fired the moment charge() saves the payment as PENDING, so the customer knows how to actually pay. */
    public static final String PAYMENT_INSTRUCTIONS_REQUIRED = "PAYMENT_INSTRUCTIONS_REQUIRED";

    public static final String SHIPPED = "SHIPPED";
    public static final String SHIPPING_FAILED = "SHIPPING_FAILED";

    public static final String DELIVERED = "DELIVERED";
    public static final String DELIVERY_FAILED = "DELIVERY_FAILED";

    /** Raw fact, published on every reserve()/addStock() mutation — the input inventory-service's InventoryStreamsTopology derives LOW_STOCK_DETECTED from. */
    public static final String STOCK_LEVEL_CHANGED = "STOCK_LEVEL_CHANGED";
    /** Derived by InventoryStreamsTopology from STOCK_LEVEL_CHANGED — edge-triggered (fires once per dip below threshold, not once per update; see its Kafka Streams Processor's state store). */
    public static final String LOW_STOCK_DETECTED = "LOW_STOCK_DETECTED";

    private EventTypes() {
    }
}
