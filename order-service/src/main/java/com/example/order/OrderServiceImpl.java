package com.example.order;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.common.model.Address;
import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderViewRepository orderViewRepository;
    private final InventoryServiceClient inventoryServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MessageSource messageSource;

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    @Override
    @Transactional
    public Order placeOrder(String keycloakUserId, String email, String productId, int quantity, Address shippingAddress,
                             PaymentMethod paymentMethod, ShippingCarrier shippingCarrier) {
        boolean reserved = inventoryServiceClient.reserve(productId, quantity);
        if (!reserved) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message("order.insufficientStock"));
        }

        Order order = new Order(
                keycloakUserId, email, productId, quantity, shippingAddress, paymentMethod, shippingCarrier, OrderStatus.PENDING_PAYMENT);
        order = orderRepository.save(order);
        orderViewRepository.save(OrderView.from(order));

        kafkaTemplate.send(Topics.ORDER_EVENTS, DomainEvent.of(EventTypes.ORDER_CREATED, order.getId().toString(), Map.of(
                "userId", keycloakUserId,
                "email", email == null ? "" : email,
                "productId", productId,
                "quantity", quantity,
                "paymentMethod", paymentMethod.name(),
                "shippingCarrier", shippingCarrier.name()
        )));

        return order;
    }

    @Override
    public List<OrderView> listOrders(String keycloakUserId) {
        return orderViewRepository.findByKeycloakUserIdAndDeletedFalse(keycloakUserId);
    }

    @Override
    public OrderView getOrderView(String id) {
        return orderViewRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Override
    @Transactional
    public void advanceStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        order.setStatus(newStatus);
        orderViewRepository.findById(orderId.toString()).ifPresent(view -> {
            view.setStatus(newStatus);
            orderViewRepository.save(view);
        });
        kafkaTemplate.send(Topics.ORDER_EVENTS, DomainEvent.of(EventTypes.ORDER_STATUS_CHANGED, orderId.toString(), Map.of(
                "status", newStatus.name(),
                "email", order.getEmail() == null ? "" : order.getEmail()
        )));
    }

    @Override
    @Transactional
    public void cancelAndReleaseStock(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        inventoryServiceClient.release(order.getProductId(), order.getQuantity());
        advanceStatus(orderId, OrderStatus.CANCELLED);
    }

    @Override
    @Transactional
    public OrderView cancelOrder(String keycloakUserId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.getKeycloakUserId().equals(keycloakUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message("order.notYourOrder"));
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message("order.tooLateToCancel", order.getStatus()));
        }
        cancelAndReleaseStock(orderId);
        return getOrderView(orderId.toString());
    }

    @Override
    @Transactional
    public OrderView updateShippingAddress(String keycloakUserId, Long orderId, Address shippingAddress) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.getKeycloakUserId().equals(keycloakUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message("order.notYourOrder"));
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message("order.tooLateToChangeAddress", order.getStatus()));
        }
        order.setShippingAddress(shippingAddress);
        orderViewRepository.findById(orderId.toString()).ifPresent(view -> {
            view.setShippingAddress(shippingAddress);
            orderViewRepository.save(view);
        });
        return getOrderView(orderId.toString());
    }

    @Override
    @Transactional
    public void delete(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        order.markDeleted();
        orderViewRepository.findByIdAndDeletedFalse(orderId.toString()).ifPresent(view -> {
            view.markDeleted();
            orderViewRepository.save(view);
        });
    }
}
