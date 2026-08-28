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

    /** Fired when a warehouse line drops below its reorder threshold — edge-triggered, see inventory-service's ReorderPolicy. */
    public static final String LOW_STOCK_DETECTED = "LOW_STOCK_DETECTED";

    private EventTypes() {
    }
}
