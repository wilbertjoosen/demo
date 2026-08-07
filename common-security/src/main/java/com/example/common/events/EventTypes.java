package com.example.common.events;

public final class EventTypes {

    public static final String USER_REGISTERED = "USER_REGISTERED";

    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";

    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";

    public static final String SHIPPED = "SHIPPED";
    public static final String SHIPPING_FAILED = "SHIPPING_FAILED";

    public static final String DELIVERED = "DELIVERED";
    public static final String DELIVERY_FAILED = "DELIVERY_FAILED";

    private EventTypes() {
    }
}
