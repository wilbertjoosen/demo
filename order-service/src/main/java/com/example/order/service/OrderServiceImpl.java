package com.example.order.service;
import com.example.order.enums.OrderStatus;
import com.example.order.model.Order;
import com.example.order.model.OrderView;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OrderViewRepository;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.common.model.Address;
import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderViewRepository orderViewRepository;
    private final InventoryServiceClient inventoryServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MessageSource messageSource;
    // order-view is sharded on keycloakUserId (see OrderView's javadoc), not _id — MongoRepository's
    // save() upserts by filtering on _id alone, which MongoDB rejects on a sharded collection
    // ("could not extract exact shard key") since that filter can't route the write to a shard.
    // Every update below goes through MongoTemplate.upsert() instead, with keycloakUserId in the
    // query so Mongo can target the write; a brand-new OrderView is inserted directly (insert()
    // writes the whole document, shard key included, so no filter-targeting issue there).
    private final MongoTemplate mongoTemplate;

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
        orderViewRepository.insert(OrderView.from(order));

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
            updateOrderView(view, Update.update("status", newStatus).set("updatedAt", view.getUpdatedAt()));
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
            updateOrderView(view, Update.update("shippingAddress", shippingAddress).set("updatedAt", view.getUpdatedAt()));
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
            updateOrderView(view, Update.update("deleted", true)
                    .set("deletedAt", view.getDeletedAt())
                    .set("updatedAt", view.getUpdatedAt()));
        });
    }

    // See the mongoTemplate field comment: order-view is sharded on keycloakUserId, so the update's
    // query must include it alongside _id for MongoDB to be able to route the write to a shard.
    private void updateOrderView(OrderView view, Update update) {
        Query query = Query.query(Criteria.where("id").is(view.getId()).and("keycloakUserId").is(view.getKeycloakUserId()));
        mongoTemplate.upsert(query, update, OrderView.class);
    }
}
