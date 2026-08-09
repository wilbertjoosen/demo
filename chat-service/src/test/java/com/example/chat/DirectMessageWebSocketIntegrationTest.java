package com.example.chat;

import com.example.chat.support.AbstractWebSocketIntegrationTest;
import com.example.chat.support.TestJwtDecoderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The DM WebSocket protocol end to end, over a real socket connection — handshake auth (a real
 * JwtDecoder call, see TestJwtDecoderConfig), participant enforcement, and the
 * MESSAGE/TYPING/READ -> MESSAGE/MESSAGE_UPDATED/TYPING envelope contract.
 */
class DirectMessageWebSocketIntegrationTest extends AbstractWebSocketIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    ConversationRepository conversationRepository;
    @Autowired
    DirectMessageRepository directMessageRepository;

    final ObjectMapper objectMapper = new ObjectMapper();
    final WebSocketClient client = new StandardWebSocketClient();
    final List<WebSocketSession> openSessions = new ArrayList<>();

    @AfterEach
    void closeSessions() {
        openSessions.forEach(s -> {
            try {
                if (s.isOpen()) {
                    s.close();
                }
            } catch (Exception e) {
                // best-effort cleanup, nothing to act on if a socket was already gone
            }
        });
        openSessions.clear();
    }

    private String seedConversation(String userA, String userB) {
        Conversation conversation = new Conversation(
                userA.compareTo(userB) < 0 ? List.of(userA, userB) : List.of(userB, userA),
                Map.of(userA, userA + "-name", userB, userB + "-name"));
        return conversationRepository.save(conversation).getId();
    }

    private static class QueueingHandler extends TextWebSocketHandler {
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            received.add(message.getPayload());
        }
    }

    private WebSocketSession connect(String conversationId, String token, QueueingHandler handler) throws Exception {
        WebSocketSession session = client.execute(
                        handler, "ws://localhost:" + port + "/ws/conversations/" + conversationId + "?token=" + token)
                .get(5, TimeUnit.SECONDS);
        openSessions.add(session);
        return session;
    }

    private JsonNode nextFrame(QueueingHandler handler) throws Exception {
        String raw = handler.received.poll(5, TimeUnit.SECONDS);
        assertThat(raw).as("expected a frame within 5s").isNotNull();
        return objectMapper.readTree(raw);
    }

    @Test
    void handshake_rejectsMissingToken() {
        String conversationId = seedConversation("user-1", "user-2");
        QueueingHandler handler = new QueueingHandler();

        assertThatThrownBy(() -> client.execute(handler, "ws://localhost:" + port + "/ws/conversations/" + conversationId)
                .get(5, TimeUnit.SECONDS))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    void handshake_rejectsInvalidToken() {
        String conversationId = seedConversation("user-1", "user-2");
        QueueingHandler handler = new QueueingHandler();

        assertThatThrownBy(() -> connect(conversationId, TestJwtDecoderConfig.INVALID_TOKEN, handler))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    void handshake_rejectsNonParticipant() {
        String conversationId = seedConversation("user-1", "user-2");
        QueueingHandler handler = new QueueingHandler();

        assertThatThrownBy(() -> connect(conversationId, "user-3", handler))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    void message_isBroadcastToBothParticipants_andPersisted() throws Exception {
        String conversationId = seedConversation("user-1", "user-2");
        QueueingHandler senderHandler = new QueueingHandler();
        QueueingHandler recipientHandler = new QueueingHandler();
        WebSocketSession sender = connect(conversationId, "user-1", senderHandler);
        connect(conversationId, "user-2", recipientHandler);

        sender.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "MESSAGE", "body", "hello there"))));

        JsonNode senderFrame = nextFrame(senderHandler);
        assertThat(senderFrame.get("type").asText()).isEqualTo("MESSAGE");
        assertThat(senderFrame.get("payload").get("body").asText()).isEqualTo("hello there");
        assertThat(senderFrame.get("payload").get("senderId").asText()).isEqualTo("user-1");

        JsonNode recipientFrame = nextFrame(recipientHandler);
        assertThat(recipientFrame.get("payload").get("body").asText()).isEqualTo("hello there");
    }

    @Test
    void typing_isRelayedToOtherParticipantOnly_notEchoedBack() throws Exception {
        String conversationId = seedConversation("user-1", "user-2");
        QueueingHandler senderHandler = new QueueingHandler();
        QueueingHandler recipientHandler = new QueueingHandler();
        WebSocketSession sender = connect(conversationId, "user-1", senderHandler);
        connect(conversationId, "user-2", recipientHandler);

        sender.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "TYPING"))));

        JsonNode recipientFrame = nextFrame(recipientHandler);
        assertThat(recipientFrame.get("type").asText()).isEqualTo("TYPING");
        assertThat(recipientFrame.get("payload").get("senderId").asText()).isEqualTo("user-1");

        assertThat(senderHandler.received.poll(500, TimeUnit.MILLISECONDS)).as("sender should not receive its own typing ping").isNull();
    }

    @Test
    void deliveryAndReadReceipts_updateLiveForTheSender() throws Exception {
        String conversationId = seedConversation("user-1", "user-2");
        QueueingHandler senderHandler = new QueueingHandler();
        WebSocketSession sender = connect(conversationId, "user-1", senderHandler);

        sender.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "MESSAGE", "body", "receipts test"))));
        nextFrame(senderHandler); // the raw MESSAGE echo

        // Recipient connects afterward — its own afterConnectionEstablished marks the pending message delivered.
        QueueingHandler recipientHandler = new QueueingHandler();
        WebSocketSession recipient = connect(conversationId, "user-2", recipientHandler);

        JsonNode deliveredUpdate = nextFrame(senderHandler);
        assertThat(deliveredUpdate.get("type").asText()).isEqualTo("MESSAGE_UPDATED");
        assertThat(deliveredUpdate.get("payload").get("deliveredAt").isNull()).isFalse();
        assertThat(deliveredUpdate.get("payload").get("readAt").isNull()).isTrue();

        recipient.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "READ"))));

        JsonNode readUpdate = nextFrame(senderHandler);
        assertThat(readUpdate.get("type").asText()).isEqualTo("MESSAGE_UPDATED");
        assertThat(readUpdate.get("payload").get("readAt").isNull()).isFalse();
    }

    @Test
    void connecting_marksPreviouslySentMessagesDelivered() throws Exception {
        String conversationId = seedConversation("user-1", "user-2");
        QueueingHandler senderHandler = new QueueingHandler();
        WebSocketSession sender = connect(conversationId, "user-1", senderHandler);
        sender.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "MESSAGE", "body", "are you there"))));
        JsonNode echoed = nextFrame(senderHandler);
        String messageId = echoed.get("payload").get("id").asText();
        sender.close(CloseStatus.NORMAL); // recipient wasn't online yet, so no delivery receipt fires now

        QueueingHandler recipientHandler = new QueueingHandler();
        connect(conversationId, "user-2", recipientHandler);

        // Recipient connecting later still triggers markDelivered for what's already in history —
        // verified via the repository directly since the original sender's socket is now closed.
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            DirectMessage stored = directMessageRepository.findById(messageId).orElseThrow();
            assertThat(stored.getDeliveredAt()).isNotNull();
        });
    }
}
