package com.example.product.model;

import lombok.Getter;
import lombok.Setter;

/** One row of sample-products.csv (name,sku,price). */
@Getter
@Setter
public class ProductCsvRow {

    private String name;
    private String sku;
    private String price;
}
