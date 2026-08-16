package com.example.chat;

import com.example.chat.controller.ConversationController;

import com.example.chat.support.AbstractRestIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.function.Consumer;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST + real MongoDB persistence for the private-conversation endpoints (ConversationController).
 * The WebSocket protocol (delivery/read receipts, typing) is covered separately in
 * DirectMessageWebSocketIntegrationTest, which needs a real running server rather than MockMvc.
 */
class ConversationRestApiIntegrationTest extends AbstractRestIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private static RequestPostProcessor userToken(String subject) {
        return userToken(subject, jwt -> { });
    }

    private static RequestPostProcessor userToken(String subject, Consumer<Jwt.Builder> customizer) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> {
                    jwt.subject(subject);
                    jwt.claim("preferred_username", subject);
                    customizer.accept(jwt);
                })
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void startConversation_thenAppearsInBothParticipantsInboxes() throws Exception {
        String body = mockMvc.perform(post("/api/conversations")
                        .with(userToken("user-1"))
                        .contentType("application/json")
                        .content("""
                                {"otherUserId":"user-2","otherUsername":"bob"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String conversationId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(get("/api/conversations").with(userToken("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].otherParticipantUsername").value("bob"))
                .andExpect(jsonPath("$[0].id").value(conversationId));

        mockMvc.perform(get("/api/conversations").with(userToken("user-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].otherParticipantUsername").value("user-1"));
    }

    @Test
    void startConversation_withSelf_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .with(userToken("user-3"))
                        .contentType("application/json")
                        .content("""
                                {"otherUserId":"user-3","otherUsername":"self"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startConversation_isIdempotent_reusesExistingConversation() throws Exception {
        String first = mockMvc.perform(post("/api/conversations")
                        .with(userToken("user-4"))
                        .contentType("application/json")
                        .content("""
                                {"otherUserId":"user-5","otherUsername":"carol"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/conversations")
                        .with(userToken("user-5"))
                        .contentType("application/json")
                        .content("""
                                {"otherUserId":"user-4","otherUsername":"dave"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String firstId = objectMapper.readTree(first).get("id").asText();
        String secondId = objectMapper.readTree(second).get("id").asText();
        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void messages_nonParticipant_returnsForbidden() throws Exception {
        String body = mockMvc.perform(post("/api/conversations")
                        .with(userToken("user-6"))
                        .contentType("application/json")
                        .content("""
                                {"otherUserId":"user-7","otherUsername":"eve"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String conversationId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(get("/api/conversations/{id}/messages", conversationId).with(userToken("user-8")))
                .andExpect(status().isForbidden());
    }

    @Test
    void messages_unknownConversation_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/conversations/{id}/messages", "000000000000000000000000").with(userToken("user-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized());
    }
}
