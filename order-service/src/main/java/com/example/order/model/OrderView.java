package com.example.order.model;
import com.example.order.enums.OrderStatus;

import com.example.common.model.Address;
import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Query-side (read) model — CQRS: kept in sync by the same Kafka listeners the saga already
 * requires this service to have (see OrderSagaListener), so this read model is close to free.
 */
@Document(collection = "order-view")
public class OrderView {

    @Id
    private String id;

    private String keycloakUserId;
    private String productId;
    private int quantity;
    private Address shippingAddress;
    private PaymentMethod paymentMethod;
    private ShippingCarrier shippingCarrier;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean deleted = false;
    private Instant deletedAt;

    public OrderView() {
    }

    public static OrderView from(Order order) {
        OrderView view = new OrderView();
        view.id = order.getId().toString();
        view.keycloakUserId = order.getKeycloakUserId();
        view.productId = order.getProductId();
        view.quantity = order.getQuantity();
        view.shippingAddress = order.getShippingAddress();
        view.paymentMethod = order.getPaymentMethod();
        view.shippingCarrier = order.getShippingCarrier();
        view.status = order.getStatus();
        view.createdAt = order.getCreatedAt();
        view.updatedAt = order.getUpdatedAt();
        return view;
    }

    public String getId() {
        return id;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Address getShippingAddress() {
        return shippingAddress;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public ShippingCarrier getShippingCarrier() {
        return shippingCarrier;
    }

    public void setShippingAddress(Address shippingAddress) {
        this.shippingAddress = shippingAddress;
        this.updatedAt = Instant.now();
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
