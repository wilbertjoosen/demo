package com.example.product.config;

import com.example.product.model.Product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * product-service does not enable {@code spring.data.mongodb.auto-index-creation} (same as
 * user-service — see UserIndexConfig there), so the one index this service actually depends on for
 * correctness is created explicitly here, once, on startup.
 * <p>
 * Partial + unique on {@code sku}: this is the real guard against duplicate products, making
 * ProductServiceImpl.create() genuinely idempotent — a retried create (double-click, client
 * timeout-then-retry) hits this constraint instead of silently inserting a second product with the
 * same sku. Scoped to non-deleted products so a deleted product's sku can be reused. If existing
 * data already violates this, {@code ensureIndex} fails on startup — deliberately loud: the
 * duplicates need cleaning before the constraint can hold.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductIndexConfig {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        String name = mongoTemplate.indexOps(Product.class).ensureIndex(
                new Index()
                        .named("uniq_sku_active")
                        .on("sku", Sort.Direction.ASC)
                        .unique()
                        .partial(PartialIndexFilter.of(Criteria.where("deleted").is(false))));

        log.info("Ensured Mongo index '{}' on products.sku (unique, partial)", name);
    }
}
