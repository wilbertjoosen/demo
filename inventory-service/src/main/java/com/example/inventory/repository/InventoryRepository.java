package com.example.inventory.repository;

import com.example.inventory.model.InventoryItem;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends MongoRepository<InventoryItem, String> {

    List<InventoryItem> findByProductIdAndDeletedFalse(String productId);

    Optional<InventoryItem> findByProductIdAndWarehouseId(String productId, String warehouseId);

    Optional<InventoryItem> findByIdAndDeletedFalse(String id);
}
