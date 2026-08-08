package com.example.chat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

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

    void markDelivered() {
        if (this.deliveredAt == null) {
            this.deliveredAt = Instant.now();
        }
    }

    void markRead() {
        markDelivered();
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
    }
}
