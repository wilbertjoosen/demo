package com.example.reference.model;

import com.example.reference.controller.CountryController;

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
                linkTo(methodOn(CountryController.class).getByCode(country.getCode())).withSelfRel());
    }

    @Override
    public CollectionModel<EntityModel<Country>> toCollectionModel(Iterable<? extends Country> countries) {
        CollectionModel<EntityModel<Country>> model = RepresentationModelAssembler.super.toCollectionModel(countries);
        return model.add(linkTo(methodOn(CountryController.class).list()).withSelfRel());
    }
}
