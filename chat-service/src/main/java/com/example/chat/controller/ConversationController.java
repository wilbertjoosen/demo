package com.example.chat.controller;
import com.example.chat.model.Conversation;
import com.example.chat.model.ConversationSummary;
import com.example.chat.model.DirectMessage;
import com.example.chat.service.ConversationService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Private user-to-user direct messages — separate from the public per-product chat rooms handled by ChatController. */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public List<ConversationSummary> myConversations(@AuthenticationPrincipal Jwt jwt) {
        return conversationService.myConversations(jwt.getSubject());
    }

    public record StartConversationRequest(String otherUserId, String otherUsername) {
    }

    /** Idempotent: returns the existing conversation with this user if one already exists. */
    @PostMapping
    public Conversation start(@AuthenticationPrincipal Jwt jwt, @RequestBody StartConversationRequest request) {
        return conversationService.startOrGet(jwt.getSubject(), jwt.getClaimAsString("preferred_username"),
                request.otherUserId(), request.otherUsername());
    }

    @GetMapping("/{id}/messages")
    public List<DirectMessage> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                         @RequestParam(defaultValue = "50") int limit) {
        return conversationService.history(id, jwt.getSubject(), limit);
    }
}
