package com.example.shipping.saga;

import com.example.shipping.service.ShippingService;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.common.model.ShippingCarrier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShippingSagaListener {

    /** Fallback for PAYMENT_COMPLETED events published before shippingCarrier existed in the payload. */
    private static final ShippingCarrier DEFAULT_CARRIER = ShippingCarrier.UPS;

    private final ShippingService shippingService;

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "shipping-service")
    public void onPaymentEvent(DomainEvent event) {
        if (EventTypes.PAYMENT_COMPLETED.equals(event.eventType())) {
            Map<?, ?> payload = (Map<?, ?>) event.payload();
            int quantity = ((Number) payload.get("quantity")).intValue();
            String email = (String) payload.get("email");
            String userId = (String) payload.get("userId");
            String carrierName = (String) payload.get("shippingCarrier");
            ShippingCarrier carrier;
            if (carrierName == null) {
                log.warn("PAYMENT_COMPLETED for order {} has no shippingCarrier (pre-dates this field) — defaulting to {}",
                        event.orderId(), DEFAULT_CARRIER);
                carrier = DEFAULT_CARRIER;
            } else {
                carrier = ShippingCarrier.valueOf(carrierName);
            }
            shippingService.ship(event.orderId(), userId, email, quantity, carrier);
        }
    }
}
