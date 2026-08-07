package com.example.shipping;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ShippingSagaListener {

    private final ShippingService shippingService;

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "shipping-service")
    public void onPaymentEvent(DomainEvent event) {
        if (EventTypes.PAYMENT_COMPLETED.equals(event.eventType())) {
            Map<?, ?> payload = (Map<?, ?>) event.payload();
            int quantity = ((Number) payload.get("quantity")).intValue();
            String email = (String) payload.get("email");
            shippingService.ship(event.orderId(), email, quantity);
        }
    }
}
