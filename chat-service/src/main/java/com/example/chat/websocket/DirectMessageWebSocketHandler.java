package com.example.chat.websocket;
import com.example.chat.model.DirectMessage;
import com.example.chat.model.TypingEvent;
import com.example.chat.model.WsEnvelope;
import com.example.chat.service.ConversationService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One room per conversationId; at most its 2 participants can ever join a given room (enforced at
 * handshake). Inbound frames are a small JSON envelope ({"type":"MESSAGE","body":"..."} /
 * {"type":"TYPING"} / {"type":"READ"}) rather than a raw string, so the same socket carries chat
 * messages, typing pings, and read receipts. Outbound frames are {@link WsEnvelope}: MESSAGE (a new
 * message), MESSAGE_UPDATED (delivered/read status changed on an existing message), TYPING.
 */
@Component
@RequiredArgsConstructor
public class DirectMessageWebSocketHandler extends TextWebSocketHandler {

    private final ConversationService conversationService;
    /** The Spring-managed bean (not a locally `new`'d one) so Instant serializes as ISO-8601, matching
     * Spring Boot's default Jackson config — a bare `new ObjectMapper()` writes it as epoch-seconds instead. */
    private final ObjectMapper objectMapper;

    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String conversationId = conversationId(session);
        rooms.computeIfAbsent(conversationId, id -> ConcurrentHashMap.newKeySet()).add(session);
        // Everything the other participant already sent is now delivered, since I just connected.
        List<DirectMessage> delivered = conversationService.markDelivered(conversationId, keycloakId(session));
        broadcastUpdates(conversationId, delivered);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<WebSocketSession> room = rooms.get(conversationId(session));
        if (room != null) {
            room.remove(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode frame;
        try {
            frame = objectMapper.readTree(message.getPayload());
        } catch (IOException e) {
            return;
        }
        String conversationId = conversationId(session);
        String keycloakId = keycloakId(session);
        String username = username(session);

        switch (frame.path("type").asText("")) {
            case "MESSAGE" -> handleMessage(session, conversationId, keycloakId, username, frame.path("body").asText(""));
            case "TYPING" -> broadcastToOthers(session, conversationId, new WsEnvelope("TYPING", new TypingEvent(keycloakId, username)));
            case "READ" -> broadcastUpdates(conversationId, conversationService.markRead(conversationId, keycloakId));
            default -> { }
        }
    }

    private void handleMessage(
            WebSocketSession session, String conversationId, String keycloakId, String username, String body) throws IOException {
        if (body == null || body.isBlank()) {
            return;
        }
        DirectMessage saved = conversationService.sendMessage(conversationId, keycloakId, username, body);
        broadcast(conversationId, new WsEnvelope("MESSAGE", saved));

        // If the recipient is already connected to this room, it's delivered immediately rather than
        // waiting for their next connect.
        String recipientId = otherConnectedParticipant(session, conversationId, keycloakId);
        if (recipientId != null) {
            broadcastUpdates(conversationId, conversationService.markDelivered(conversationId, recipientId));
        }
    }

    private String otherConnectedParticipant(WebSocketSession session, String conversationId, String excludeKeycloakId) {
        Set<WebSocketSession> room = rooms.get(conversationId);
        if (room == null) {
            return null;
        }
        for (WebSocketSession other : room) {
            if (other != session && other.isOpen()) {
                String otherKeycloakId = keycloakId(other);
                if (!otherKeycloakId.equals(excludeKeycloakId)) {
                    return otherKeycloakId;
                }
            }
        }
        return null;
    }

    private void broadcastUpdates(String conversationId, List<DirectMessage> updated) throws IOException {
        for (DirectMessage message : updated) {
            broadcast(conversationId, new WsEnvelope("MESSAGE_UPDATED", message));
        }
    }

    private void broadcast(String conversationId, WsEnvelope envelope) throws IOException {
        Set<WebSocketSession> room = rooms.get(conversationId);
        if (room == null) {
            return;
        }
        send(room, envelope);
    }

    private void broadcastToOthers(WebSocketSession session, String conversationId, WsEnvelope envelope) throws IOException {
        Set<WebSocketSession> room = rooms.get(conversationId);
        if (room == null) {
            return;
        }
        Set<WebSocketSession> others = ConcurrentHashMap.newKeySet();
        for (WebSocketSession s : room) {
            if (s != session) {
                others.add(s);
            }
        }
        send(others, envelope);
    }

    private void send(Set<WebSocketSession> sessions, WsEnvelope envelope) throws IOException {
        TextMessage payload = new TextMessage(objectMapper.writeValueAsString(envelope));
        for (WebSocketSession session : sessions) {
            try {
                // Tomcat's WS RemoteEndpoint isn't safe for concurrent sends to the same session; serialize per-session.
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(payload);
                    }
                }
            } catch (IOException e) {
                sessions.remove(session);
            }
        }
    }

    private String conversationId(WebSocketSession session) {
        return String.valueOf(session.getAttributes().get(DirectMessageHandshakeInterceptor.CONVERSATION_ID_ATTR));
    }

    private String keycloakId(WebSocketSession session) {
        return String.valueOf(session.getAttributes().get(DirectMessageHandshakeInterceptor.KEYCLOAK_ID_ATTR));
    }

    private String username(WebSocketSession session) {
        return String.valueOf(session.getAttributes().get(DirectMessageHandshakeInterceptor.USERNAME_ATTR));
    }
}
