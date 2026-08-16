package com.example.chat.websocket;

import com.example.chat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Unlike the public per-product chat room (ChatHandshakeInterceptor), direct-message conversations are
 * private, so the handshake actually authenticates the caller instead of trusting a self-reported
 * username. Browsers still can't set an Authorization header on a WS handshake, but they can put
 * arbitrary query params on the URL, so the access token travels as ?token=... and is validated here
 * with the same JwtDecoder every REST endpoint uses. The connection is rejected unless the token is
 * valid AND the caller is one of the conversation's two participants.
 */
@Component
@RequiredArgsConstructor
public class DirectMessageHandshakeInterceptor implements HandshakeInterceptor {

    static final String CONVERSATION_ID_ATTR = "conversationId";
    static final String KEYCLOAK_ID_ATTR = "keycloakId";
    static final String USERNAME_ATTR = "username";

    private final JwtDecoder jwtDecoder;
    private final ConversationService conversationService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        String uri = servletRequest.getServletRequest().getRequestURI();
        String conversationId = uri.substring(uri.lastIndexOf('/') + 1);

        String token = extractParam(request.getURI().getQuery(), "token");
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String keycloakId = jwt.getSubject();
        try {
            conversationService.assertParticipant(conversationId, keycloakId);
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(CONVERSATION_ID_ATTR, conversationId);
        attributes.put(KEYCLOAK_ID_ATTR, keycloakId);
        attributes.put(USERNAME_ATTR, jwt.getClaimAsString("preferred_username"));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractParam(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
