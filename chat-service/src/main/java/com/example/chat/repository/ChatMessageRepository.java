package com.example.chat.repository;
import com.example.chat.model.ChatMessage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ReadPreference;

import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    /**
     * Public per-product chat room history — read-heavy, stale-tolerant (see product-service's
     * ProductRepository for the full reasoning). Unlike this, DirectMessageRepository (private 1:1
     * messages) deliberately stays on the default read preference: a user expects to see their own
     * just-sent message immediately, not after replication lag.
     */
    @ReadPreference("secondaryPreferred")
    List<ChatMessage> findByProductIdOrderByCreatedAtDesc(String productId, Pageable pageable);
}
