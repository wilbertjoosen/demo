package com.example.order;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
class OrderViewModelAssembler implements RepresentationModelAssembler<OrderView, EntityModel<OrderView>> {

    @Override
    public EntityModel<OrderView> toModel(OrderView order) {
        return EntityModel.of(order,
                linkTo(methodOn(OrderController.class).getOrder(order.getId())).withSelfRel(),
                linkTo(methodOn(OrderController.class).myOrders(null)).withRel("orders"));
    }

    @Override
    public CollectionModel<EntityModel<OrderView>> toCollectionModel(Iterable<? extends OrderView> orders) {
        CollectionModel<EntityModel<OrderView>> model = RepresentationModelAssembler.super.toCollectionModel(orders);
        return model.add(linkTo(methodOn(OrderController.class).myOrders(null)).withSelfRel());
    }
}
