package com.example.user.model;

import com.example.user.controller.UserController;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class CountryModelAssembler implements RepresentationModelAssembler<Country, EntityModel<Country>> {

    @Override
    public EntityModel<Country> toModel(Country country) {
        return EntityModel.of(country,
                linkTo(methodOn(UserController.class).getById(country.getCode())).withSelfRel());
    }

    @Override
    public CollectionModel<EntityModel<Country>> toCollectionModel(Iterable<? extends Country> contries) {
        CollectionModel<EntityModel<Country>> model = RepresentationModelAssembler.super.toCollectionModel(contries);
        return model.add(linkTo(methodOn(UserController.class).list()).withSelfRel());
    }
}
