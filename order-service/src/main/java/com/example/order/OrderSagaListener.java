package com.example.order;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Choreographed saga, order-service's side: react to every downstream step to advance or cancel
 * the order. No central orchestrator — each service only knows how to react to the event before it.
 */
@Component
@RequiredArgsConstructor
public class OrderSagaListener {

    private final OrderService orderService;

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "order-service")
    public void onPaymentEvent(DomainEvent event) {
        Long orderId = Long.valueOf(event.orderId());
        switch (event.eventType()) {
            case EventTypes.PAYMENT_COMPLETED -> orderService.advanceStatus(orderId, OrderStatus.PAID);
            case EventTypes.PAYMENT_FAILED, EventTypes.PAYMENT_REFUNDED -> orderService.cancelAndReleaseStock(orderId);
            default -> { }
        }
    }

    @KafkaListener(topics = Topics.SHIPPING_EVENTS, groupId = "order-service")
    public void onShippingEvent(DomainEvent event) {
        if (EventTypes.SHIPPED.equals(event.eventType())) {
            orderService.advanceStatus(Long.valueOf(event.orderId()), OrderStatus.SHIPPED);
        }
        // SHIPPING_FAILED is handled by payment-service (issues the refund); order-service reacts
        // to the resulting PAYMENT_REFUNDED event above rather than duplicating that logic here.
    }

    @KafkaListener(topics = Topics.DELIVERY_EVENTS, groupId = "order-service")
    public void onDeliveryEvent(DomainEvent event) {
        if (EventTypes.DELIVERED.equals(event.eventType())) {
            orderService.advanceStatus(Long.valueOf(event.orderId()), OrderStatus.CONFIRMED);
        }
        // DELIVERY_FAILED: same compensation path as SHIPPING_FAILED, via payment-service's refund.
    }
}
