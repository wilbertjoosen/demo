package com.example.order;
import com.example.order.enums.OrderStatus;
import com.example.order.model.Order;
import com.example.order.model.OrderView;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OrderViewRepository;
import com.example.order.service.InventoryServiceClient;
import com.example.order.service.OrderServiceImpl;

import com.example.common.events.EventContracts;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.common.model.Address;
import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderViewRepository orderViewRepository;
    @Mock
    InventoryServiceClient inventoryServiceClient;
    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    MessageSource messageSource;

    OrderServiceImpl orderService;

    static final Address ADDRESS = new Address("1 Main St", "Springfield", "12345", "US");

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, orderViewRepository, inventoryServiceClient, kafkaTemplate, messageSource);
    }

    private Order savedOrder(Long id, String keycloakUserId, OrderStatus status) {
        Order order = new Order(keycloakUserId, "buyer@example.com", "product-1", 2, ADDRESS,
                PaymentMethod.CREDIT_CARD, ShippingCarrier.UPS, status);
        // Order#id is DB-generated; reflectively set it the way JPA would after save() in these unit tests.
        try {
            var field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return order;
    }

    @Test
    void placeOrder_reservesStockPersistsAndPublishesOrderCreated() {
        when(inventoryServiceClient.reserve("product-1", 2)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> savedOrder(42L, "user-1", OrderStatus.PENDING_PAYMENT));

        Order result = orderService.placeOrder("user-1", "buyer@example.com", "product-1", 2, ADDRESS,
                PaymentMethod.CREDIT_CARD, ShippingCarrier.UPS);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(orderViewRepository).save(any(OrderView.class));

        ArgumentCaptor<com.example.common.events.DomainEvent> eventCaptor =
                ArgumentCaptor.forClass(com.example.common.events.DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.ORDER_EVENTS), eventCaptor.capture());
        com.example.common.events.DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.ORDER_CREATED);
        assertThat(published.orderId()).isEqualTo("42");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("userId", "user-1").containsEntry("productId", "product-1").containsEntry("quantity", 2);
        // Contract test: payment-service, reporting-service and others consume ORDER_CREATED
        // relying on exactly these fields being present — see EventContracts.
        assertThat(EventContracts.missingFields(EventTypes.ORDER_CREATED, payload)).isEmpty();
    }

    @Test
    void placeOrder_insufficientStock_throwsConflictAndPublishesNothing() {
        when(inventoryServiceClient.reserve("product-1", 2)).thenReturn(false);
        when(messageSource.getMessage(eq("order.insufficientStock"), any(), any())).thenReturn("insufficient stock");

        assertThatThrownBy(() -> orderService.placeOrder("user-1", "buyer@example.com", "product-1", 2, ADDRESS,
                PaymentMethod.CREDIT_CARD, ShippingCarrier.UPS))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("insufficient stock");

        verify(orderRepository, never()).save(any());
        verify(orderViewRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void advanceStatus_updatesBothWriteAndReadModel_andPublishesOrderStatusChanged() {
        Order order = savedOrder(1L, "user-1", OrderStatus.PENDING_PAYMENT);
        OrderView view = OrderView.from(order);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderViewRepository.findById("1")).thenReturn(Optional.of(view));

        orderService.advanceStatus(1L, OrderStatus.PAID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(view.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderViewRepository).save(view);

        ArgumentCaptor<com.example.common.events.DomainEvent> eventCaptor =
                ArgumentCaptor.forClass(com.example.common.events.DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.ORDER_EVENTS), eventCaptor.capture());
        com.example.common.events.DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.ORDER_STATUS_CHANGED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("status", "PAID");
        // Contract test: reporting-service's order-metrics KTable relies on exactly these fields —
        // see EventContracts and ReportingTopology.
        assertThat(EventContracts.missingFields(EventTypes.ORDER_STATUS_CHANGED, payload)).isEmpty();
    }

    @Test
    void advanceStatus_unknownOrder_isNoOp() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        orderService.advanceStatus(999L, OrderStatus.PAID);

        verify(orderViewRepository, never()).findById(anyString());
        verify(orderViewRepository, never()).save(any());
    }

    @Test
    void cancelAndReleaseStock_releasesStockAndCancels() {
        Order order = savedOrder(1L, "user-1", OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderViewRepository.findById("1")).thenReturn(Optional.of(OrderView.from(order)));

        orderService.cancelAndReleaseStock(1L);

        verify(inventoryServiceClient).release("product-1", 2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelAndReleaseStock_alreadyCancelled_doesNotReleaseTwice() {
        Order order = savedOrder(1L, "user-1", OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.cancelAndReleaseStock(1L);

        verify(inventoryServiceClient, never()).release(anyString(), anyInt());
    }

    @Test
    void cancelOrder_wrongUser_throwsForbidden() {
        Order order = savedOrder(1L, "owner", OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(messageSource.getMessage(eq("order.notYourOrder"), any(), any())).thenReturn("not your order");

        assertThatThrownBy(() -> orderService.cancelOrder("someone-else", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not your order");

        verify(inventoryServiceClient, never()).release(anyString(), anyInt());
    }

    @Test
    void cancelOrder_pastPendingPayment_throwsConflict() {
        Order order = savedOrder(1L, "owner", OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(messageSource.getMessage(eq("order.tooLateToCancel"), any(), any())).thenReturn("too late");

        assertThatThrownBy(() -> orderService.cancelOrder("owner", 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("too late");

        verify(inventoryServiceClient, never()).release(anyString(), anyInt());
    }

    @Test
    void cancelOrder_happyPath_releasesStockCancelsAndPublishes() {
        Order order = savedOrder(1L, "owner", OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderViewRepository.findById("1")).thenReturn(Optional.of(OrderView.from(order)));
        when(orderViewRepository.findByIdAndDeletedFalse("1")).thenReturn(Optional.of(OrderView.from(order)));

        OrderView result = orderService.cancelOrder("owner", 1L);

        assertThat(result).isNotNull();
        verify(inventoryServiceClient).release("product-1", 2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(kafkaTemplate).send(eq(Topics.ORDER_EVENTS), any());
    }

    @Test
    void updateShippingAddress_wrongUser_throwsForbidden() {
        Order order = savedOrder(1L, "owner", OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(messageSource.getMessage(eq("order.notYourOrder"), any(), any())).thenReturn("not your order");
        Address newAddress = new Address("2 Second St", "Shelbyville", "54321", "US");

        assertThatThrownBy(() -> orderService.updateShippingAddress("someone-else", 1L, newAddress))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateShippingAddress_happyPath_updatesBothModels() {
        Order order = savedOrder(1L, "owner", OrderStatus.PENDING_PAYMENT);
        OrderView view = OrderView.from(order);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderViewRepository.findById("1")).thenReturn(Optional.of(view));
        when(orderViewRepository.findByIdAndDeletedFalse("1")).thenReturn(Optional.of(view));
        Address newAddress = new Address("2 Second St", "Shelbyville", "54321", "US");

        orderService.updateShippingAddress("owner", 1L, newAddress);

        assertThat(order.getShippingAddress().getStreet()).isEqualTo("2 Second St");
        assertThat(view.getShippingAddress().getStreet()).isEqualTo("2 Second St");
    }

    @Test
    void delete_marksBothWriteAndReadModelDeleted() {
        Order order = savedOrder(1L, "owner", OrderStatus.CONFIRMED);
        OrderView view = OrderView.from(order);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderViewRepository.findByIdAndDeletedFalse("1")).thenReturn(Optional.of(view));

        orderService.delete(1L);

        assertThat(view.isDeleted()).isTrue();
        verify(orderViewRepository, times(1)).save(view);
    }
}
