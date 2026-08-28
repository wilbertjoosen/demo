package com.example.inventory.service;
import com.example.inventory.model.InventoryItem;
import com.example.inventory.policy.ReorderPolicy;
import com.example.inventory.ports.StockAlertPort;
import com.example.inventory.repository.InventoryRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final String PRODUCT_ID = "productId";
    private static final String WAREHOUSE_ID = "warehouseId";
    private static final String QUANTITY = "quantity";
    private static final String DELETED = "deleted";
    private static final String UPDATED_AT = "updatedAt";
    private static final String LAST_MODIFIED_BY = "lastModifiedBy";
    private static final String AGGREGATE_CACHE = "inventoryAggregate";
    private static final String LOW_STOCK_ALERTED = "lowStockAlerted";

    private final InventoryRepository inventoryRepository;
    private final MongoTemplate mongoTemplate;
    private final AuditorAware<String> auditorAware;
    private final ReorderPolicy reorderPolicy;
    private final StockAlertPort stockAlertPort;

    private String currentAuditor() {
        return auditorAware.getCurrentAuditor().orElse("system");
    }

    @Override
    @CacheEvict(value = AGGREGATE_CACHE, key = "#productId")
    public boolean reserve(String productId, int quantity) {
        Query query = Query.query(Criteria.where(PRODUCT_ID).is(productId)
                .and(WAREHOUSE_ID).is(InventoryItem.DEFAULT_WAREHOUSE)
                .and(DELETED).is(false)
                .and(QUANTITY).gte(quantity));
        Update update = new Update().inc(QUANTITY, -quantity)
                .set(UPDATED_AT, Instant.now())
                .set(LAST_MODIFIED_BY, currentAuditor());
        InventoryItem updated = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), InventoryItem.class);
        if (updated != null) {
            maybeAlertLowStock(updated);
        }
        return updated != null;
    }

    /**
     * Edge-triggered: only publishes once per dip below threshold, not once per reserve() call.
     * The second {@code findAndModify} is a compare-and-set on {@code lowStockAlerted} — under
     * concurrent reserves only the caller that flips false→true gets to publish.
     */
    private void maybeAlertLowStock(InventoryItem afterUpdate) {
        if (!reorderPolicy.isLowStock(afterUpdate.getQuantity())) {
            return;
        }
        Query armQuery = Query.query(Criteria.where("id").is(afterUpdate.getId()).and(LOW_STOCK_ALERTED).is(false));
        Update armUpdate = new Update().set(LOW_STOCK_ALERTED, true);
        InventoryItem armed = mongoTemplate.findAndModify(armQuery, armUpdate,
                FindAndModifyOptions.options().returnNew(true), InventoryItem.class);
        if (armed != null) {
            stockAlertPort.publishLowStock(armed, reorderPolicy.threshold());
        }
    }

    /** Restocking above threshold resets the latch so a future dip alerts again. */
    private void maybeDisarmLowStock(InventoryItem afterUpdate) {
        if (afterUpdate == null || !afterUpdate.isLowStockAlerted() || reorderPolicy.isLowStock(afterUpdate.getQuantity())) {
            return;
        }
        Query query = Query.query(Criteria.where("id").is(afterUpdate.getId()));
        mongoTemplate.updateFirst(query, new Update().set(LOW_STOCK_ALERTED, false), InventoryItem.class);
    }

    @Override
    @CacheEvict(value = AGGREGATE_CACHE, key = "#productId")
    public void release(String productId, int quantity) {
        Query query = Query.query(Criteria.where(PRODUCT_ID).is(productId)
                .and(WAREHOUSE_ID).is(InventoryItem.DEFAULT_WAREHOUSE)
                .and(DELETED).is(false));
        Update update = new Update().inc(QUANTITY, quantity)
                .set(UPDATED_AT, Instant.now())
                .set(LAST_MODIFIED_BY, currentAuditor());
        mongoTemplate.findAndModify(query, update, InventoryItem.class);
    }

    @Override
    @Cacheable(value = AGGREGATE_CACHE, key = "#productId")
    public int aggregateStock(String productId) {
        return listByProduct(productId).stream().mapToInt(InventoryItem::getQuantity).sum();
    }

    @Override
    public List<InventoryItem> listByProduct(String productId) {
        return inventoryRepository.findByProductIdAndDeletedFalse(productId);
    }

    @Override
    @CacheEvict(value = AGGREGATE_CACHE, key = "#productId")
    public InventoryItem addStock(String productId, String warehouseId, int quantity) {
        String auditor = currentAuditor();
        Query query = Query.query(Criteria.where(PRODUCT_ID).is(productId).and(WAREHOUSE_ID).is(warehouseId));
        Update update = new Update()
                .inc(QUANTITY, quantity)
                .set(UPDATED_AT, Instant.now())
                .set(LAST_MODIFIED_BY, auditor)
                .setOnInsert(PRODUCT_ID, productId)
                .setOnInsert(WAREHOUSE_ID, warehouseId)
                .setOnInsert(DELETED, false)
                .setOnInsert("createdAt", Instant.now())
                .setOnInsert("createdBy", auditor);
        InventoryItem result = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true).upsert(true), InventoryItem.class);
        maybeDisarmLowStock(result);
        return result;
    }

    @Override
    public void provisionForNewProduct(String productId) {
        if (inventoryRepository.findByProductIdAndWarehouseId(productId, InventoryItem.DEFAULT_WAREHOUSE).isEmpty()) {
            inventoryRepository.save(new InventoryItem(productId, InventoryItem.DEFAULT_WAREHOUSE, 0));
        }
    }

    /** {@code id} is the warehouse-item's own id, not the productId — evict the whole cache rather than guess the key. */
    @Override
    @CacheEvict(value = AGGREGATE_CACHE, allEntries = true)
    public void delete(String id) {
        Query query = Query.query(Criteria.where("id").is(id).and(DELETED).is(false));
        Update update = new Update().set(DELETED, true).set("deletedAt", Instant.now())
                .set(UPDATED_AT, Instant.now())
                .set(LAST_MODIFIED_BY, currentAuditor());
        InventoryItem updated = mongoTemplate.findAndModify(query, update, InventoryItem.class);
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
