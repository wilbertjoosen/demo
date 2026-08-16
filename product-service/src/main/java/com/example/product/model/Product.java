package com.example.product.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/** Catalog metadata only — stock lives in inventory-service (per-warehouse, aggregated there). */
@Document(collection = "products")
@Getter
@NoArgsConstructor
public class Product {

    @Id
    private String id;

    private String sku;
    private String name;
    private BigDecimal price;

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

    public Product(String sku, String name, BigDecimal price) {
        this.sku = sku;
        this.name = name;
        this.price = price;
    }

    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
