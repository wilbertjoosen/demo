package com.example.comment.model;
import com.example.comment.controller.CommentController;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class CommentModelAssembler implements RepresentationModelAssembler<Comment, EntityModel<Comment>> {

    @Override
    public EntityModel<Comment> toModel(Comment comment) {
        return EntityModel.of(comment, linkTo(methodOn(CommentController.class).list(comment.getProductId())).withRel("comments"));
    }

    @Override
    public CollectionModel<EntityModel<Comment>> toCollectionModel(Iterable<? extends Comment> comments) {
        return RepresentationModelAssembler.super.toCollectionModel(comments);
    }
}
