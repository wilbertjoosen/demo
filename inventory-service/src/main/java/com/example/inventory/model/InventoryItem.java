package com.example.inventory.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One row per (productId, warehouseId) — a product's total stock is the sum across warehouses.
 *
 * <p>Most mutations here go through {@code MongoTemplate.findAndModify} (atomic stock ops), which
 * bypasses Spring Data's auditing entity callbacks — {@code InventoryServiceImpl} sets
 * {@code lastModifiedBy}/{@code updatedAt} manually on those update paths instead.
 *
 * <p>Compound indexes serve findByProductIdAndDeletedFalse(productId) and
 * findByProductIdAndWarehouseId(productId, warehouseId) — the latter is this document's natural
 * composite key per the javadoc above.
 */
@CompoundIndexes({
        @CompoundIndex(def = "{'productId': 1, 'deleted': 1}"),
        @CompoundIndex(def = "{'productId': 1, 'warehouseId': 1}")
})
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

    /** Edge-trigger latch for LOW_STOCK_DETECTED — true once alerted, reset on restock above threshold. */
    private boolean lowStockAlerted = false;

    public InventoryItem(String productId, String warehouseId, int quantity) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
    }
}
