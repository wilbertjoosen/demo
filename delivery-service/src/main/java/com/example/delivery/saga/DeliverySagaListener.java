package com.example.delivery.saga;
import com.example.delivery.service.DeliveryService;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DeliverySagaListener {

    private final DeliveryService deliveryService;

    @KafkaListener(topics = Topics.SHIPPING_EVENTS, groupId = "delivery-service")
    public void onShippingEvent(DomainEvent event) {
        if (EventTypes.SHIPPED.equals(event.eventType())) {
            Map<?, ?> payload = (Map<?, ?>) event.payload();
            int quantity = ((Number) payload.get("quantity")).intValue();
            String email = (String) payload.get("email");
            deliveryService.deliver(event.orderId(), email, quantity);
        }
    }
}
