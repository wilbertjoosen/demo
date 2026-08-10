package com.example.delivery;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventContracts;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceImplTest {

    @Mock
    DeliveryRepository deliveryRepository;
    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    DeliveryServiceImpl deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryServiceImpl(deliveryRepository, kafkaTemplate);
    }

    private Delivery withId(Delivery delivery, String id) {
        try {
            var field = Delivery.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(delivery, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return delivery;
    }

    @Test
    void deliver_normalQuantity_savesPendingDeliveryAgentWithoutPublishing() {
        when(deliveryRepository.save(any(Delivery.class))).thenAnswer(inv -> withId(inv.getArgument(0), "del-1"));

        deliveryService.deliver("order-1", "a@b.com", 2);

        ArgumentCaptor<Delivery> deliveryCaptor = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveryRepository).save(deliveryCaptor.capture());
        Delivery saved = deliveryCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.PENDING_DELIVERY_AGENT);
        assertThat(saved.getQuantity()).isEqualTo(2);
        assertThat(saved.getEmail()).isEqualTo("a@b.com");
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void confirmDelivered_pendingDeliveryAgent_deliversAndPublishesDelivered() {
        Delivery pending = withId(new Delivery("order-1", DeliveryStatus.PENDING_DELIVERY_AGENT, "a@b.com", 4), "del-2");
        when(deliveryRepository.findByIdAndDeletedFalse("del-2")).thenReturn(Optional.of(pending));

        deliveryService.confirmDelivered("del-2");

        assertThat(pending.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        verify(deliveryRepository).save(pending);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.DELIVERY_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.DELIVERED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("quantity", 4).containsEntry("email", "a@b.com");
        assertThat(EventContracts.missingFields(EventTypes.DELIVERED, payload)).isEmpty();
    }

    @Test
    void confirmDelivered_notPendingDeliveryAgent_throwsConflict() {
        Delivery delivered = withId(new Delivery("order-1", DeliveryStatus.DELIVERED), "del-3");
        when(deliveryRepository.findByIdAndDeletedFalse("del-3")).thenReturn(Optional.of(delivered));

        assertThatThrownBy(() -> deliveryService.confirmDelivered("del-3")).isInstanceOf(ResponseStatusException.class);
        verify(deliveryRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void reportIssue_pendingDeliveryAgent_failsAndPublishesDeliveryFailed() {
        Delivery pending = withId(new Delivery("order-1", DeliveryStatus.PENDING_DELIVERY_AGENT, "a@b.com", 3), "del-4");
        when(deliveryRepository.findByIdAndDeletedFalse("del-4")).thenReturn(Optional.of(pending));

        deliveryService.reportIssue("del-4", "customer unavailable");

        assertThat(pending.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(pending.getIssueReason()).isEqualTo("customer unavailable");
        verify(deliveryRepository).save(pending);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.DELIVERY_EVENTS), eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(EventTypes.DELIVERY_FAILED);
    }

    @Test
    void deliver_simulatedFailureQuantity_publishesDeliveryFailed() {
        deliveryService.deliver("order-1", "a@b.com", 11);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.DELIVERY_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.DELIVERY_FAILED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(EventContracts.missingFields(EventTypes.DELIVERY_FAILED, payload)).isEmpty();
    }

    @Test
    void delete_found_marksDeleted() {
        Delivery delivery = new Delivery("order-1", DeliveryStatus.DELIVERED);
        when(deliveryRepository.findByIdAndDeletedFalse("d1")).thenReturn(Optional.of(delivery));

        deliveryService.delete("d1");

        assertThat(delivery.isDeleted()).isTrue();
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void delete_notFound_throwsNotFound() {
        when(deliveryRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.delete("missing")).isInstanceOf(ResponseStatusException.class);
        verify(deliveryRepository, never()).save(any());
    }
}
