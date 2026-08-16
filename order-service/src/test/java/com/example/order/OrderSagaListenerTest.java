package com.example.order;

import com.example.order.enums.OrderStatus;
import com.example.order.saga.OrderSagaListener;
import com.example.order.service.OrderService;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * order-service's side of the choreographed saga: no orchestrator, just react to whatever event
 * arrives. These tests pin down that mapping so a future refactor can't silently drop a case.
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaListenerTest {

    @Mock
    OrderService orderService;

    OrderSagaListener listener;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        listener = new OrderSagaListener(orderService);
    }

    @Test
    void paymentCompleted_advancesToPaid() {
        listener.onPaymentEvent(DomainEvent.of(EventTypes.PAYMENT_COMPLETED, "42", Map.of()));

        verify(orderService).advanceStatus(42L, OrderStatus.PAID);
    }

    @Test
    void paymentFailed_cancelsAndReleasesStock() {
        listener.onPaymentEvent(DomainEvent.of(EventTypes.PAYMENT_FAILED, "42", Map.of()));

        verify(orderService).cancelAndReleaseStock(42L);
        verify(orderService, never()).advanceStatus(42L, OrderStatus.PAID);
    }

    @Test
    void paymentRefunded_cancelsAndReleasesStock() {
        listener.onPaymentEvent(DomainEvent.of(EventTypes.PAYMENT_REFUNDED, "42", Map.of()));

        verify(orderService).cancelAndReleaseStock(42L);
    }

    @Test
    void shipped_advancesToShipped() {
        listener.onShippingEvent(DomainEvent.of(EventTypes.SHIPPED, "42", Map.of()));

        verify(orderService).advanceStatus(42L, OrderStatus.SHIPPED);
    }

    @Test
    void shippingFailed_isIgnored_compensationHandledByPaymentService() {
        listener.onShippingEvent(DomainEvent.of(EventTypes.SHIPPING_FAILED, "42", Map.of()));

        verify(orderService, never()).advanceStatus(42L, OrderStatus.SHIPPED);
        verify(orderService, never()).cancelAndReleaseStock(42L);
    }

    @Test
    void delivered_advancesToConfirmed() {
        listener.onDeliveryEvent(DomainEvent.of(EventTypes.DELIVERED, "42", Map.of()));

        verify(orderService).advanceStatus(42L, OrderStatus.CONFIRMED);
    }

    @Test
    void deliveryFailed_isIgnored_compensationHandledByPaymentService() {
        listener.onDeliveryEvent(DomainEvent.of(EventTypes.DELIVERY_FAILED, "42", Map.of()));

        verify(orderService, never()).advanceStatus(42L, OrderStatus.CONFIRMED);
        verify(orderService, never()).cancelAndReleaseStock(42L);
    }
}
