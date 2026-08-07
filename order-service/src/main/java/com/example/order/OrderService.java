package com.example.order;

import com.example.common.model.Address;

import java.util.List;

public interface OrderService {

    /**
     * Command side: reserve stock, persist in one local transaction, kick off the saga.
     * {@code email} (from the JWT, not stored) is carried forward into every downstream saga
     * event's payload — event-carried state transfer, so notification-service can email the actual
     * customer instead of a hardcoded address, without any service needing to call user-service.
     */
    Order placeOrder(String keycloakUserId, String email, String productId, int quantity, Address shippingAddress);

    /** Query side (CQRS): served from the Mongo read model, not MySQL. */
    List<OrderView> listOrders(String keycloakUserId);

    OrderView getOrderView(String id);

    void advanceStatus(Long orderId, OrderStatus newStatus);

    void cancelAndReleaseStock(Long orderId);

    /**
     * User-initiated cancellation. Only allowed while PENDING_PAYMENT — once payment-service has
     * started processing, cancelling would need a coordinated refund + shipping-cancel, which is a
     * bigger saga-orchestration case than this demo covers; that window rejects with 409 instead.
     */
    OrderView cancelOrder(String keycloakUserId, Long orderId);

    /**
     * Address-only update, also PENDING_PAYMENT-gated. Changing quantity isn't supported here — it
     * would mean adjusting the existing stock reservation's delta atomically, a meaningfully bigger
     * feature; cancel and re-order instead if the quantity needs to change.
     */
    OrderView updateShippingAddress(String keycloakUserId, Long orderId, Address shippingAddress);

    /** Admin-only soft delete — marks both the write model (MySQL) and the read model (Mongo) deleted. */
    void delete(Long orderId);
}
