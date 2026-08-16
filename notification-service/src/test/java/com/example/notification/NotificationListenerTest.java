package com.example.notification;
import com.example.notification.saga.NotificationListener;
import com.example.notification.websocket.NotificationWebSocketHandler;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    NotificationWebSocketHandler webSocketHandler;
    @Mock
    JavaMailSender mailSender;

    NotificationListener notificationListener;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        // Spring Boot auto-registers JavaTimeModule on the real ObjectMapper bean (via
        // spring-boot-starter-json) — matched here explicitly since this test builds the listener
        // by hand rather than through a Spring context; DomainEvent.timestamp() is an Instant, and
        // a raw ObjectMapper can't serialize that without it.
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        notificationListener = new NotificationListener(webSocketHandler, mailSender, objectMapper);
        setField("fallbackEmail", "admin@example.com");
        setField("fromEmail", "demo@example.com");
    }

    private void setField(String name, Object value) throws ReflectiveOperationException {
        var field = NotificationListener.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(notificationListener, value);
    }

    @Test
    void onEvent_withEmailInPayload_broadcastsAndEmailsThatAddress() {
        DomainEvent event = DomainEvent.of(EventTypes.ORDER_CREATED, "order-1", Map.of("email", "buyer@example.com"));

        notificationListener.onEvent(event);

        verify(webSocketHandler).broadcast(anyString());
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("buyer@example.com");
        assertThat(captor.getValue().getSubject()).contains("ORDER_CREATED").contains("order-1");
    }

    @Test
    void onEvent_withNoEmailInPayload_fallsBackToAdminAddress() {
        DomainEvent event = DomainEvent.of(EventTypes.PRODUCT_CREATED, null, Map.of("productId", "p1"));

        notificationListener.onEvent(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("admin@example.com");
    }

    @Test
    void onEvent_broadcastsSerializedEventJson() {
        DomainEvent event = DomainEvent.of(EventTypes.ORDER_CREATED, "order-1", Map.of("email", "buyer@example.com"));

        notificationListener.onEvent(event);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(webSocketHandler).broadcast(jsonCaptor.capture());
        assertThat(jsonCaptor.getValue()).contains("ORDER_CREATED").contains("order-1");
    }
}
