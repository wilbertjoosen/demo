package com.example.notification.saga;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.notification.mail.EmailDispatcher;
import com.example.notification.websocket.NotificationWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final NotificationWebSocketHandler webSocketHandler;
    private final EmailDispatcher emailDispatcher;
    private final ObjectMapper objectMapper;

    /** Fallback recipient only for events with no specific user in scope (e.g. PRODUCT_CREATED — products aren't tied to a customer). */
    @Value("${notifications.fallback-email:admin@example.com}")
    private String fallbackEmail;

    @Value("${notifications.mail.from-email:demo@example.com}")
    private String fromEmail;

    // concurrency matches KafkaTopicAutoConfiguration's partition count (common-security) — Kafka
    // only ever hands one consumer per partition per group, so this would silently do nothing
    // against the broker's old 1-partition auto-create default (see that class's javadoc).
    @KafkaListener(topics = {
            Topics.USER_EVENTS, Topics.PRODUCT_EVENTS, Topics.ORDER_EVENTS,
            Topics.PAYMENT_EVENTS, Topics.SHIPPING_EVENTS, Topics.DELIVERY_EVENTS, Topics.INVENTORY_EVENTS
    }, groupId = "notification-service", concurrency = "${kafka.topics.partitions:3}")
    public void onEvent(DomainEvent event) {
        broadcast(event);
        emailFor(event);
    }

    private void broadcast(DomainEvent event) {
        try {
            webSocketHandler.broadcast(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize event {} for WebSocket broadcast", event.eventType(), e);
        }
    }

    private void emailFor(DomainEvent event) {
        String recipient = recipientEmail(event);
        SimpleMailMessage message = EventTypes.PAYMENT_INSTRUCTIONS_REQUIRED.equals(event.eventType())
                ? paymentInstructionsMessage(event, recipient)
                : genericMessage(event, recipient);
        emailDispatcher.send(message);
    }

    private SimpleMailMessage genericMessage(DomainEvent event, String recipient) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(recipient);
        message.setSubject("[demo] " + event.eventType() + (event.orderId() != null ? " (order " + event.orderId() + ")" : ""));
        message.setText("Event: " + event.eventType() + "\nOrder: " + event.orderId() + "\nPayload: " + event.payload());
        return message;
    }

    /** BANK_TRANSFER/CASH-specific payment instructions, richer than the generic status-update email every other event gets. */
    private SimpleMailMessage paymentInstructionsMessage(DomainEvent event, String recipient) {
        String method = event.payload() instanceof Map<?, ?> payload ? String.valueOf(payload.get("method")) : "";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(recipient);
        message.setSubject("[demo] Payment instructions for order " + event.orderId());
        message.setText("BANK_TRANSFER".equals(method) ? bankTransferInstructions(event.orderId()) : cashInstructions(event.orderId()));
        return message;
    }

    private String bankTransferInstructions(String orderId) {
        return "Thanks for your order " + orderId + "!\n\n"
                + "To complete your payment by bank transfer, use the following details:\n"
                + "  Bank: Demo Bank\n"
                + "  Account holder: Demo Store Inc.\n"
                + "  IBAN: DE00 0000 0000 0000 0000 00\n"
                + "  Reference: " + orderId + "\n\n"
                + "Please include the reference above so we can match your payment. Your order will be "
                + "reviewed once the transfer is confirmed.\n";
    }

    private String cashInstructions(String orderId) {
        return "Thanks for your order " + orderId + "!\n\n"
                + "To complete your payment in cash, please visit any of our stores within 3 business days "
                + "and mention your order number " + orderId + " to the cashier.\n\n"
                + "Your order will be reviewed once the payment is confirmed.\n";
    }

    private String recipientEmail(DomainEvent event) {
        if (event.payload() instanceof Map<?, ?> payload) {
            Object email = payload.get("email");
            if (email instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return fallbackEmail;
    }
}
