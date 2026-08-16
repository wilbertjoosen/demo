package com.example.chat.model;

import com.example.chat.controller.ChatController;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ChatModelAssembler implements RepresentationModelAssembler<ChatMessage, EntityModel<ChatMessage>> {

    @Override
    public EntityModel<ChatMessage> toModel(ChatMessage chatMessage) {
        return EntityModel.of(chatMessage,
                linkTo(methodOn(ChatController.class).history(chatMessage.getProductId(), 50)).withRel("messages"));
    }

    @Override
    public CollectionModel<EntityModel<ChatMessage>> toCollectionModel(Iterable<? extends ChatMessage> chatMessages) {
        return RepresentationModelAssembler.super.toCollectionModel(chatMessages);
    }
}
