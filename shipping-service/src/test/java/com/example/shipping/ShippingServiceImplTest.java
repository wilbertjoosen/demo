package com.example.shipping;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventContracts;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.common.model.ShippingCarrier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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
class ShippingServiceImplTest {

    @Mock
    ShipmentRepository shipmentRepository;
    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    ShippingRateCalculator rateCalculator;

    ShippingServiceImpl shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingServiceImpl(shipmentRepository, kafkaTemplate, rateCalculator);
    }

    @Test
    void ship_normalQuantity_dispatchesAndPublishesShipped() {
        when(rateCalculator.quote(ShippingCarrier.UPS, 2)).thenReturn(new BigDecimal("9.99"));

        shippingService.ship("order-1", "user-1", "a@b.com", 2, ShippingCarrier.UPS);

        verify(shipmentRepository).save(any(Shipment.class));
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.SHIPPING_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.SHIPPED);
        assertThat(published.orderId()).isEqualTo("order-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("quantity", 2);
        assertThat(EventContracts.missingFields(EventTypes.SHIPPED, payload)).isEmpty();
    }

    @Test
    void ship_simulatedFailureQuantity_publishesShippingFailed() {
        when(rateCalculator.quote(ShippingCarrier.UPS, 7)).thenReturn(new BigDecimal("9.99"));

        shippingService.ship("order-1", "user-1", "a@b.com", 7, ShippingCarrier.UPS);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.SHIPPING_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.SHIPPING_FAILED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(EventContracts.missingFields(EventTypes.SHIPPING_FAILED, payload)).isEmpty();
    }

    @Test
    void quote_delegatesToRateCalculator() {
        when(rateCalculator.quote(ShippingCarrier.DHL, 3)).thenReturn(new BigDecimal("15.00"));

        assertThat(shippingService.quote(ShippingCarrier.DHL, 3)).isEqualByComparingTo("15.00");
    }

    @Test
    void getByOrderId_found_returnsShipment() {
        Shipment shipment = new Shipment("order-1", "user-1", ShipmentStatus.DISPATCHED, ShippingCarrier.UPS, BigDecimal.TEN);
        when(shipmentRepository.findByOrderIdAndDeletedFalse("order-1")).thenReturn(Optional.of(shipment));

        assertThat(shippingService.getByOrderId("order-1")).isEqualTo(shipment);
    }

    @Test
    void getByOrderId_notFound_throwsNotFound() {
        when(shipmentRepository.findByOrderIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingService.getByOrderId("missing")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void delete_found_marksDeleted() {
        Shipment shipment = new Shipment("order-1", "user-1", ShipmentStatus.DISPATCHED, ShippingCarrier.UPS, BigDecimal.TEN);
        when(shipmentRepository.findByIdAndDeletedFalse("ship-1")).thenReturn(Optional.of(shipment));

        shippingService.delete("ship-1");

        assertThat(shipment.isDeleted()).isTrue();
        verify(shipmentRepository).save(shipment);
    }

    @Test
    void delete_notFound_throwsNotFound() {
        when(shipmentRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingService.delete("missing")).isInstanceOf(ResponseStatusException.class);
        verify(shipmentRepository, never()).save(any());
    }
}
