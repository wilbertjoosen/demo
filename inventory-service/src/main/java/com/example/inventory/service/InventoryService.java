package com.example.inventory.service;

import com.example.inventory.model.InventoryItem;

import java.util.List;

public interface InventoryService {

    /** True if stock was decremented from the default warehouse, false if there wasn't enough. */
    boolean reserve(String productId, int quantity);

    void release(String productId, int quantity);

    /** Sum of quantity across every warehouse for this product. */
    int aggregateStock(String productId);

    /** Per-warehouse breakdown. */
    List<InventoryItem> listByProduct(String productId);

    InventoryItem addStock(String productId, String warehouseId, int quantity);

    void provisionForNewProduct(String productId);

    /** Soft delete of one (productId, warehouseId) line — removes it from the aggregate/breakdown views. */
    void delete(String id);
}
