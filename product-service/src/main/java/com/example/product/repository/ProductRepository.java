package com.example.product.repository;

import com.example.product.model.Product;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByDeletedFalse();

    Optional<Product> findByIdAndDeletedFalse(String id);
}
