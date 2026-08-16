package com.example.review.model;

import com.example.review.controller.ReviewController;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ReviewModelAssembler implements RepresentationModelAssembler<Review, EntityModel<Review>> {

    @Override
    public EntityModel<Review> toModel(Review review) {
        return EntityModel.of(review, linkTo(methodOn(ReviewController.class).list(review.getProductId())).withRel("reviews"));
    }

    @Override
    public CollectionModel<EntityModel<Review>> toCollectionModel(Iterable<? extends Review> reviews) {
        return RepresentationModelAssembler.super.toCollectionModel(reviews);
    }
}
