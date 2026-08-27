package com.example.product.service;
import com.example.product.model.Product;

import java.util.List;

public interface ProductService {

    List<Product> list();

    Product get(String id);

    Product create(Product product);

    Product update(String id, Product update);

    /** Soft delete — marks the record instead of removing it. */
    void delete(String id);
}
