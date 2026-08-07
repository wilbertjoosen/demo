package com.example.shipping;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
class ShipmentModelAssembler implements RepresentationModelAssembler<Shipment, EntityModel<Shipment>> {

    @Override
    public EntityModel<Shipment> toModel(Shipment shipment) {
        return EntityModel.of(shipment, linkTo(methodOn(ShipmentController.class).list()).withRel("shipments"));
    }

    @Override
    public CollectionModel<EntityModel<Shipment>> toCollectionModel(Iterable<? extends Shipment> shipments) {
        CollectionModel<EntityModel<Shipment>> model = RepresentationModelAssembler.super.toCollectionModel(shipments);
        return model.add(linkTo(methodOn(ShipmentController.class).list()).withSelfRel());
    }
}
