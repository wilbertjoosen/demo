package com.example.chat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

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
