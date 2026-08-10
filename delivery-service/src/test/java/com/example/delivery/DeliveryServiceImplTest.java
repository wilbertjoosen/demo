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

    @Test
    void deliver_normalQuantity_savesDeliveredAndPublishesDelivered() {
        deliveryService.deliver("order-1", "a@b.com", 2);

        verify(deliveryRepository).save(any(Delivery.class));
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.DELIVERY_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.DELIVERED);
        assertThat(published.orderId()).isEqualTo("order-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("quantity", 2);
        assertThat(EventContracts.missingFields(EventTypes.DELIVERED, payload)).isEmpty();
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
