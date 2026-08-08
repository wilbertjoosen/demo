package com.example.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** One "room" per productId; a session only ever receives messages for the room it connected to. */
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    /** The Spring-managed bean (not a locally `new`'d one) so Instant serializes as ISO-8601, matching
     * Spring Boot's default Jackson config — a bare `new ObjectMapper()` writes it as epoch-seconds instead. */
    private final ObjectMapper objectMapper;

    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String productId = productId(session);
        rooms.computeIfAbsent(productId, id -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<WebSocketSession> room = rooms.get(productId(session));
        if (room != null) {
            room.remove(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String productId = productId(session);
        String username = username(session);
        String body = message.getPayload();
        if (body == null || body.isBlank()) {
            return;
        }
        ChatMessage saved = chatService.save(productId, username, body);
        broadcast(productId, saved);
    }

    private void broadcast(String productId, ChatMessage chatMessage) throws IOException {
        Set<WebSocketSession> room = rooms.get(productId);
        if (room == null) {
            return;
        }
        TextMessage payload = new TextMessage(objectMapper.writeValueAsString(chatMessage));
        for (WebSocketSession session : room) {
            try {
                // Tomcat's WS RemoteEndpoint isn't safe for concurrent sends to the same session
                // (two rooms' handler threads can race here); serialize per-session.
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(payload);
                    }
                }
            } catch (IOException e) {
                room.remove(session);
            }
        }
    }

    private String productId(WebSocketSession session) {
        return String.valueOf(session.getAttributes().get(ChatHandshakeInterceptor.PRODUCT_ID_ATTR));
    }

    private String username(WebSocketSession session) {
        return String.valueOf(session.getAttributes().get(ChatHandshakeInterceptor.USERNAME_ATTR));
    }
}
