package com.example.inventory.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One row per (productId, warehouseId) — a product's total stock is the sum across warehouses.
 *
 * <p>Most mutations here go through {@code MongoTemplate.findAndModify} (atomic stock ops), which
 * bypasses Spring Data's auditing entity callbacks — {@code InventoryServiceImpl} sets
 * {@code lastModifiedBy}/{@code updatedAt} manually on those update paths instead.
 */
@Document(collection = "inventory")
@Getter
@NoArgsConstructor
public class InventoryItem {

    public static final String DEFAULT_WAREHOUSE = "MAIN";

    @Id
    private String id;

    private String productId;
    private String warehouseId;
    private int quantity;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;

    private boolean deleted = false;
    private Instant deletedAt;

    public InventoryItem(String productId, String warehouseId, int quantity) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
    }
}
