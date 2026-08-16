package com.example.product.model;

import com.example.product.controller.ProductController;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ProductModelAssembler implements RepresentationModelAssembler<Product, EntityModel<Product>> {

    @Override
    public EntityModel<Product> toModel(Product product) {
        return EntityModel.of(product,
                linkTo(methodOn(ProductController.class).get(product.getId())).withSelfRel(),
                linkTo(methodOn(ProductController.class).list()).withRel("products"),
                Link.of("/api/inventory/{productId}", "inventory").expand(product.getId()));
    }

    @Override
    public CollectionModel<EntityModel<Product>> toCollectionModel(Iterable<? extends Product> products) {
        CollectionModel<EntityModel<Product>> model = RepresentationModelAssembler.super.toCollectionModel(products);
        return model.add(linkTo(methodOn(ProductController.class).list()).withSelfRel());
    }
}
