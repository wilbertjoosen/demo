package com.example.delivery.model;

import com.example.delivery.controller.DeliveryController;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class DeliveryModelAssembler implements RepresentationModelAssembler<Delivery, EntityModel<Delivery>> {

    @Override
    public EntityModel<Delivery> toModel(Delivery delivery) {
        return EntityModel.of(delivery, linkTo(methodOn(DeliveryController.class).list()).withRel("deliveries"));
    }

    @Override
    public CollectionModel<EntityModel<Delivery>> toCollectionModel(Iterable<? extends Delivery> deliveries) {
        CollectionModel<EntityModel<Delivery>> model = RepresentationModelAssembler.super.toCollectionModel(deliveries);
        return model.add(linkTo(methodOn(DeliveryController.class).list()).withSelfRel());
    }
}
