package com.example.chat.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * findByConversationIdOrderByCreatedAtDesc, findByConversationIdAndSenderIdNotAndDeliveredAtIsNull,
 * findByConversationIdAndSenderIdNotAndReadAtIsNull (+ its count variant) — all lead with
 * conversationId equality, so each compound index below still narrows on that prefix even though
 * senderId's "Not" condition itself can't use an index.
 */
@CompoundIndexes({
        @CompoundIndex(def = "{'conversationId': 1, 'createdAt': -1}"),
        @CompoundIndex(def = "{'conversationId': 1, 'deliveredAt': 1}"),
        @CompoundIndex(def = "{'conversationId': 1, 'readAt': 1}")
})
@Document(collection = "direct_messages")
@Getter
@NoArgsConstructor
public class DirectMessage {

    @Id
    private String id;

    private String conversationId;
    private String senderId;
    private String senderUsername;
    private String body;

    /** Set when the recipient's client acknowledges receipt (connects to the room, or was already connected). */
    private Instant deliveredAt;

    /** Set when the recipient has the conversation open and the message is marked read. Implies delivered. */
    private Instant readAt;

    @CreatedDate
    private Instant createdAt;

    public DirectMessage(String conversationId, String senderId, String senderUsername, String body) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.body = body;
    }

    public void markDelivered() {
        if (this.deliveredAt == null) {
            this.deliveredAt = Instant.now();
        }
    }

    public void markRead() {
        markDelivered();
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
    }
}
