package com.example.chat.config;

import com.example.chat.websocket.ChatHandshakeInterceptor;
import com.example.chat.websocket.ChatWebSocketHandler;
import com.example.chat.websocket.DirectMessageHandshakeInterceptor;
import com.example.chat.websocket.DirectMessageWebSocketHandler;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final ChatHandshakeInterceptor interceptor;
    private final DirectMessageWebSocketHandler directMessageHandler;
    private final DirectMessageHandshakeInterceptor directMessageInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat/*").addInterceptors(interceptor).setAllowedOrigins("*");
        registry.addHandler(directMessageHandler, "/ws/conversations/*").addInterceptors(directMessageInterceptor).setAllowedOrigins("*");
    }
}
