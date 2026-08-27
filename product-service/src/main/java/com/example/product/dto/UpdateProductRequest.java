package com.example.product.dto;

import com.example.product.service.ProductServiceImpl;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** Every field optional — {@link ProductServiceImpl#update} applies only the ones present. */
public record UpdateProductRequest(String sku, String name,
                                   @DecimalMin(value = "0.0", inclusive = false) BigDecimal price) {
}