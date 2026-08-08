package com.example.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DirectMessageRepository extends MongoRepository<DirectMessage, String> {

    List<DirectMessage> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    Optional<DirectMessage> findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);

    /** Messages the given recipient hasn't yet been marked as delivered for (i.e. sent by the other participant). */
    List<DirectMessage> findByConversationIdAndSenderIdNotAndDeliveredAtIsNull(String conversationId, String recipientId);

    /** Messages the given recipient hasn't yet read (sent by the other participant, delivered or not). */
    List<DirectMessage> findByConversationIdAndSenderIdNotAndReadAtIsNull(String conversationId, String recipientId);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(String conversationId, String recipientId);
}
