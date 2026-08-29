package com.example.chat.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** findByProductIdOrderByCreatedAtDesc(productId, pageable). */
@CompoundIndex(def = "{'productId': 1, 'createdAt': -1}")
@Document(collection = "chat_messages")
@Getter
@NoArgsConstructor
public class ChatMessage {

    @Id
    private String id;

    private String productId;
    private String username;
    private String body;

    @CreatedDate
    private Instant createdAt;

    public ChatMessage(String productId, String username, String body) {
        this.productId = productId;
        this.username = username;
        this.body = body;
    }
}
