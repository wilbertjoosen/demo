package com.example.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;

    @Override
    public ChatMessage save(String productId, String username, String body) {
        return chatMessageRepository.save(new ChatMessage(productId, username, body));
    }

    @Override
    public List<ChatMessage> recentHistory(String productId, int limit) {
        List<ChatMessage> newestFirst = chatMessageRepository.findByProductIdOrderByCreatedAtDesc(productId, PageRequest.of(0, limit));
        Collections.reverse(newestFirst);
        return newestFirst;
    }
}
