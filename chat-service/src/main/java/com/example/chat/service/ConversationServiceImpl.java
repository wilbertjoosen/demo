package com.example.chat.service;

import com.example.chat.model.Conversation;
import com.example.chat.model.ConversationSummary;
import com.example.chat.model.DirectMessage;
import com.example.chat.repository.ConversationRepository;
import com.example.chat.repository.DirectMessageRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final DirectMessageRepository directMessageRepository;

    @Override
    public List<ConversationSummary> myConversations(String myKeycloakId) {
        return conversationRepository.findByParticipantIdsContainingOrderByLastMessageAtDesc(myKeycloakId).stream()
                .map(conversation -> toSummary(conversation, myKeycloakId))
                .toList();
    }

    @Override
    public Conversation startOrGet(String myKeycloakId, String myUsername, String otherKeycloakId, String otherUsername) {
        if (myKeycloakId.equals(otherKeycloakId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot start a conversation with yourself");
        }
        List<String> participantIds = sortedPair(myKeycloakId, otherKeycloakId);
        return conversationRepository.findByParticipantIds(participantIds)
                .orElseGet(() -> {
                    Map<String, String> usernames = new HashMap<>();
                    usernames.put(myKeycloakId, myUsername);
                    usernames.put(otherKeycloakId, otherUsername);
                    return conversationRepository.save(new Conversation(participantIds, usernames));
                });
    }

    @Override
    public List<DirectMessage> history(String conversationId, String requesterKeycloakId, int limit) {
        assertParticipant(conversationId, requesterKeycloakId);
        List<DirectMessage> newestFirst = directMessageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, limit));
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    @Override
    public DirectMessage sendMessage(String conversationId, String senderId, String senderUsername, String body) {
        assertParticipant(conversationId, senderId);
        DirectMessage saved = directMessageRepository.save(new DirectMessage(conversationId, senderId, senderUsername, body));
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.touch();
            conversationRepository.save(conversation);
        });
        return saved;
    }

    @Override
    public List<DirectMessage> markDelivered(String conversationId, String recipientId) {
        assertParticipant(conversationId, recipientId);
        List<DirectMessage> pending = directMessageRepository.findByConversationIdAndSenderIdNotAndDeliveredAtIsNull(
                conversationId, recipientId);
        pending.forEach(DirectMessage::markDelivered);
        return directMessageRepository.saveAll(pending);
    }

    @Override
    public List<DirectMessage> markRead(String conversationId, String recipientId) {
        assertParticipant(conversationId, recipientId);
        List<DirectMessage> pending = directMessageRepository.findByConversationIdAndSenderIdNotAndReadAtIsNull(
                conversationId, recipientId);
        pending.forEach(DirectMessage::markRead);
        return directMessageRepository.saveAll(pending);
    }

    @Override
    public void assertParticipant(String conversationId, String keycloakId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!conversation.getParticipantIds().contains(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private ConversationSummary toSummary(Conversation conversation, String myKeycloakId) {
        String otherId = conversation.getParticipantIds().stream()
                .filter(id -> !id.equals(myKeycloakId))
                .findFirst()
                .orElse(myKeycloakId);
        String otherUsername = conversation.getParticipantUsernames().getOrDefault(otherId, "(unknown)");
        DirectMessage last = directMessageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId()).orElse(null);
        long unreadCount = directMessageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conversation.getId(), myKeycloakId);
        return new ConversationSummary(conversation.getId(), otherId, otherUsername, conversation.getLastMessageAt(),
                last == null ? null : last.getBody(), unreadCount);
    }

    private static List<String> sortedPair(String a, String b) {
        List<String> pair = new ArrayList<>(List.of(a, b));
        Collections.sort(pair);
        return pair;
    }
}
