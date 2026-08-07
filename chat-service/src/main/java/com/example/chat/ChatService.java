package com.example.chat;

import java.util.List;

public interface ChatService {

    ChatMessage save(String productId, String username, String body);

    /** Most recent messages first from storage, returned oldest-first for display. */
    List<ChatMessage> recentHistory(String productId, int limit);
}
