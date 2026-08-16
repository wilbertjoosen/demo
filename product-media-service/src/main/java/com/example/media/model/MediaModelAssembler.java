package com.example.media.model;

import com.example.media.controller.MediaController;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class MediaModelAssembler implements RepresentationModelAssembler<MediaAsset, EntityModel<MediaAsset>> {

    @Override
    public EntityModel<MediaAsset> toModel(MediaAsset mediaAsset) {
        return EntityModel.of(mediaAsset, linkTo(methodOn(MediaController.class).list(mediaAsset.getProductId())).withRel("media"));
    }

    @Override
    public CollectionModel<EntityModel<MediaAsset>> toCollectionModel(Iterable<? extends MediaAsset> mediaAssets) {
        return RepresentationModelAssembler.super.toCollectionModel(mediaAssets);
    }
}
