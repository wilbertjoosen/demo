package com.example.inventory;

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
class InventoryServiceImpl implements InventoryService {

    private static final String PRODUCT_ID = "productId";
    private static final String WAREHOUSE_ID = "warehouseId";
    private static final String QUANTITY = "quantity";
    private static final String DELETED = "deleted";
    private static final String UPDATED_AT = "updatedAt";
    private static final String LAST_MODIFIED_BY = "lastModifiedBy";
    private static final String AGGREGATE_CACHE = "inventoryAggregate";

    private final InventoryRepository inventoryRepository;
    private final MongoTemplate mongoTemplate;
    private final AuditorAware<String> auditorAware;

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
        return updated != null;
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
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true).upsert(true), InventoryItem.class);
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
