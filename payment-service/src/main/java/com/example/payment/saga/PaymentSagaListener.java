package com.example.payment.saga;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import com.example.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaListener {

    /** Fallbacks for ORDER_CREATED events published before these fields existed in the payload. */
    private static final PaymentMethod DEFAULT_METHOD = PaymentMethod.CREDIT_CARD;
    private static final ShippingCarrier DEFAULT_CARRIER = ShippingCarrier.UPS;

    private final PaymentService paymentService;

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "payment-service")
    public void onOrderEvent(DomainEvent event) {
        if (EventTypes.ORDER_CREATED.equals(event.eventType())) {
            Map<?, ?> payload = (Map<?, ?>) event.payload();
            int quantity = ((Number) payload.get("quantity")).intValue();
            String email = (String) payload.get("email");
            String userId = (String) payload.get("userId");
            String methodName = (String) payload.get("paymentMethod");
            String carrierName = (String) payload.get("shippingCarrier");
            if (methodName == null || carrierName == null) {
                log.warn("ORDER_CREATED for order {} predates paymentMethod/shippingCarrier — defaulting to {}/{}",
                        event.orderId(), DEFAULT_METHOD, DEFAULT_CARRIER);
            }
            PaymentMethod method = methodName == null ? DEFAULT_METHOD : PaymentMethod.valueOf(methodName);
            ShippingCarrier shippingCarrier = carrierName == null ? DEFAULT_CARRIER : ShippingCarrier.valueOf(carrierName);
            paymentService.charge(event.orderId(), userId, email, quantity, method, shippingCarrier);
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
