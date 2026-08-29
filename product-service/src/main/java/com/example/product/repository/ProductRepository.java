package com.example.product.repository;
import com.example.product.model.Product;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReadPreference;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    /**
     * The public catalog browse — highest read volume, no single caller cares about seeing a write
     * that landed milliseconds ago, so this is exactly the query worth routing off the primary.
     * Single-item lookups below stay on the default (primary) read preference instead: an admin
     * who just created/edited a product and immediately opens it shouldn't have a chance of hitting
     * a secondary that hasn't replicated that write yet.
     */
    @ReadPreference("secondaryPreferred")
    List<Product> findByDeletedFalse();

    Optional<Product> findByIdAndDeletedFalse(String id);
}
