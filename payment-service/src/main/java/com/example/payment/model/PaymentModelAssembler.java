package com.example.payment.model;
import com.example.payment.controller.PaymentController;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PaymentModelAssembler implements RepresentationModelAssembler<Payment, EntityModel<Payment>> {

    @Override
    public EntityModel<Payment> toModel(Payment payment) {
        return EntityModel.of(payment, linkTo(methodOn(PaymentController.class).list()).withRel("payments"));
    }

    @Override
    public CollectionModel<EntityModel<Payment>> toCollectionModel(Iterable<? extends Payment> payments) {
        CollectionModel<EntityModel<Payment>> model = RepresentationModelAssembler.super.toCollectionModel(payments);
        return model.add(linkTo(methodOn(PaymentController.class).list()).withSelfRel());
    }
}
