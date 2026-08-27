package com.example.product.service;
import com.example.product.model.Product;
import com.example.product.repository.ProductRepository;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String CACHE_NAME = "products";

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MongoTemplate mongoTemplate;
    private final AuditorAware<String> auditorAware;

    @Override
    @Cacheable(CACHE_NAME + "List")
    public List<Product> list() {
        return productRepository.findByDeletedFalse();
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#id")
    public Product get(String id) {
        return productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Override
    @CacheEvict(value = CACHE_NAME + "List", allEntries = true)
    public Product create(Product product) {
        Product saved = productRepository.save(product);
        kafkaTemplate.send(Topics.PRODUCT_EVENTS, DomainEvent.of(EventTypes.PRODUCT_CREATED, null, Map.of(
                "productId", saved.getId(),
                "name", saved.getName(),
                "sku", saved.getSku(),
                "price", saved.getPrice()
        )));
        return saved;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = CACHE_NAME, key = "#id"),
            @CacheEvict(value = CACHE_NAME + "List", allEntries = true)
    })
    public Product update(String id, Product patch) {
        // findAndModify + $set (partial update) rather than fetch-mutate-save — same atomic-update
        // style used for stock elsewhere in this project, and avoids ever losing the document's id.
        Update update = new Update();
        if (patch.getName() != null) {
            update.set("name", patch.getName());
        }
        if (patch.getPrice() != null) {
            update.set("price", patch.getPrice());
        }
        if (patch.getSku() != null) {
            update.set("sku", patch.getSku());
        }
        update.set("updatedAt", Instant.now());
        update.set("lastModifiedBy", auditorAware.getCurrentAuditor().orElse("system"));
        Product updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(id)), update,
                FindAndModifyOptions.options().returnNew(true), Product.class);
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        kafkaTemplate.send(Topics.PRODUCT_EVENTS, DomainEvent.of(EventTypes.PRODUCT_UPDATED, null, Map.of(
                "productId", updated.getId(),
                "name", updated.getName(),
                "sku", updated.getSku(),
                "price", updated.getPrice()
        )));
        return updated;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = CACHE_NAME, key = "#id"),
            @CacheEvict(value = CACHE_NAME + "List", allEntries = true)
    })
    public void delete(String id) {
        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        product.markDeleted();
        productRepository.save(product);
        kafkaTemplate.send(Topics.PRODUCT_EVENTS, DomainEvent.of(EventTypes.PRODUCT_DELETED, null, Map.of(
                "productId", id
        )));
    }
}
