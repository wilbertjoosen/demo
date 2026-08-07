package com.example.payment;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentSagaListener {

    private final PaymentService paymentService;

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "payment-service")
    public void onOrderEvent(DomainEvent event) {
        if (EventTypes.ORDER_CREATED.equals(event.eventType())) {
            Map<?, ?> payload = (Map<?, ?>) event.payload();
            int quantity = ((Number) payload.get("quantity")).intValue();
            String email = (String) payload.get("email");
            paymentService.charge(event.orderId(), email, quantity);
        }
    }

    @KafkaListener(topics = Topics.SHIPPING_EVENTS, groupId = "payment-service")
    public void onShippingEvent(DomainEvent event) {
        if (EventTypes.SHIPPING_FAILED.equals(event.eventType())) {
            paymentService.refund(event.orderId());
        }
    }

    @KafkaListener(topics = Topics.DELIVERY_EVENTS, groupId = "payment-service")
    public void onDeliveryEvent(DomainEvent event) {
        if (EventTypes.DELIVERY_FAILED.equals(event.eventType())) {
            paymentService.refund(event.orderId());
        }
    }
}
