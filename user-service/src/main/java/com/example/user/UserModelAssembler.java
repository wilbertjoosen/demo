package com.example.user;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
class UserModelAssembler implements RepresentationModelAssembler<UserProfileView, EntityModel<UserProfileView>> {

    @Override
    public EntityModel<UserProfileView> toModel(UserProfileView user) {
        return EntityModel.of(user,
                linkTo(methodOn(UserController.class).getById(user.id())).withSelfRel());
    }

    @Override
    public CollectionModel<EntityModel<UserProfileView>> toCollectionModel(Iterable<? extends UserProfileView> users) {
        CollectionModel<EntityModel<UserProfileView>> model = RepresentationModelAssembler.super.toCollectionModel(users);
        return model.add(linkTo(methodOn(UserController.class).list()).withSelfRel());
    }
}
