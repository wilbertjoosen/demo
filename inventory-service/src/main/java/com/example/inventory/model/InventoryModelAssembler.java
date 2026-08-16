package com.example.inventory.model;

import com.example.inventory.controller.InventoryController;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class InventoryModelAssembler implements RepresentationModelAssembler<InventoryItem, EntityModel<InventoryItem>> {

    @Override
    public EntityModel<InventoryItem> toModel(InventoryItem item) {
        return EntityModel.of(item,
                linkTo(methodOn(InventoryController.class).warehouses(item.getProductId())).withRel("warehouses"),
                linkTo(methodOn(InventoryController.class).aggregate(item.getProductId())).withRel("aggregate"));
    }

    public CollectionModel<EntityModel<InventoryItem>> toCollectionModel(Iterable<InventoryItem> items, String productId) {
        CollectionModel<EntityModel<InventoryItem>> model = RepresentationModelAssembler.super.toCollectionModel(items);
        return model.add(linkTo(methodOn(InventoryController.class).warehouses(productId)).withSelfRel());
    }
}
