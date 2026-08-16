package com.example.chat.websocket;

import com.example.chat.config.WebSocketSecurityConfig;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Browsers' native WebSocket API can't set an Authorization header (see WebSocketSecurityConfig),
 * so the room (productId) and the display name (username) are taken from the handshake URL instead
 * of a verified JWT — acceptable for a demo chat where messages aren't sensitive, but the username is
 * self-reported, not authenticated.
 */
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    static final String PRODUCT_ID_ATTR = "productId";
    static final String USERNAME_ATTR = "username";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String uri = servletRequest.getServletRequest().getRequestURI();
            String productId = uri.substring(uri.lastIndexOf('/') + 1);
            attributes.put(PRODUCT_ID_ATTR, productId);
        }
        String username = request.getURI().getQuery() == null ? "anonymous" : extractParam(request.getURI().getQuery(), "username");
        attributes.put(USERNAME_ATTR, username == null ? "anonymous" : username);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractParam(String query, String key) {
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
