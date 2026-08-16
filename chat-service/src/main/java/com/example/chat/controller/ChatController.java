package com.example.chat.controller;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.ChatModelAssembler;
import com.example.chat.service.ChatService;

import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatModelAssembler assembler;

    @GetMapping("/messages")
    public CollectionModel<EntityModel<ChatMessage>> history(@RequestParam String productId, @RequestParam(defaultValue = "50") int limit) {
        return assembler.toCollectionModel(chatService.recentHistory(productId, limit));
    }
}
