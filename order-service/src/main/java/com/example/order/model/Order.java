package com.example.order.model;
import com.example.order.controller.OrderController;
import com.example.order.enums.OrderStatus;

import com.example.common.model.Address;
import com.example.common.model.PaymentMethod;
import com.example.common.model.ShippingCarrier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Command-side (write) model — one local ACID transaction per order, the concrete reason
 * this service still uses MySQL/JPA instead of MongoDB like its siblings.
 *
 * <p>{@code @SQLRestriction} applies "deleted = false" to every Hibernate-generated query for this
 * entity automatically (find, findAll, derived queries) — unlike the Mongo-backed services, no
 * per-repository findByDeletedFalse methods are needed.
 */
@Entity
@Table(name = "orders")
@SQLRestriction("deleted = false")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String keycloakUserId;

    /** From the placing user's JWT at order-creation time — not authoritative, just carried for notifications. */
    private String email;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Embedded
    private Address shippingAddress;

    /**
     * Not DB-NOT-NULL: {@code @NotNull} on OrderController's PlaceOrderRequest already guarantees
     * every new order has one, but a hard NOT NULL here would break Hibernate's ddl-auto=update
     * against pre-existing rows from before this field existed — nullable at the column level,
     * mandatory at the API level.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.VARCHAR)} forces a plain VARCHAR column instead of
     * Hibernate 6's MySQL-dialect default of a native SQL ENUM — ddl-auto=update never widens an
     * existing ENUM column's value list when the Java enum grows, so a new PaymentMethod constant
     * (e.g. BANK_TRANSFER) silently became un-persistable ("Data truncated for column
     * 'payment_method'") against any already-deployed database. VARCHAR has no such fixed value
     * list to fall out of sync. Applied to every @Enumerated(STRING) field on this entity for the
     * same reason — status/shippingCarrier haven't drifted yet, but the failure mode is identical
     * and only reproduces against a live ddl-auto=update database, never a fresh Testcontainers one.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ShippingCarrier shippingCarrier;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private OrderStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;

    @Column(nullable = false)
    private boolean deleted = false;

    private Instant deletedAt;

    public Order(String keycloakUserId, String email, String productId, int quantity, Address shippingAddress,
                 PaymentMethod paymentMethod, ShippingCarrier shippingCarrier, OrderStatus status) {
        this.keycloakUserId = keycloakUserId;
        this.email = email;
        this.productId = productId;
        this.quantity = quantity;
        this.shippingAddress = shippingAddress;
        this.paymentMethod = paymentMethod;
        this.shippingCarrier = shippingCarrier;
        this.status = status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setShippingAddress(Address shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
