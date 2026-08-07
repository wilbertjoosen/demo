package com.example.notification;

import com.example.common.events.DomainEvent;
import com.example.common.events.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final NotificationWebSocketHandler webSocketHandler;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    /** Fallback recipient only for events with no specific user in scope (e.g. PRODUCT_CREATED — products aren't tied to a customer). */
    @Value("${notifications.fallback-email:admin@example.com}")
    private String fallbackEmail;

    @Value("${notifications.mail.from-email:demo@example.com}")
    private String fromEmail;

    @KafkaListener(topics = {
            Topics.USER_EVENTS, Topics.PRODUCT_EVENTS, Topics.ORDER_EVENTS,
            Topics.PAYMENT_EVENTS, Topics.SHIPPING_EVENTS, Topics.DELIVERY_EVENTS
    }, groupId = "notification-service")
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(recipient);
        message.setSubject("[demo] " + event.eventType() + (event.orderId() != null ? " (order " + event.orderId() + ")" : ""));
        message.setText("Event: " + event.eventType() + "\nOrder: " + event.orderId() + "\nPayload: " + event.payload());
        mailSender.send(message);
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
