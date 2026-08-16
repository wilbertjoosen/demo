package com.example.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Browsers' native WebSocket API can't set an Authorization header, so the /ws handshake can't go
 * through the shared JWT-required chain (ResourceServerSecurityConfig). This chain is matched first
 * (lower @Order value) and permits just /ws/**; everything else — including /api/notifications/**  —
 * still falls through to the shared chain and requires a valid JWT. The broadcast content itself
 * (order/product status changes) isn't per-user-sensitive, so an open WS endpoint is an acceptable
 * simplification for a demo.
 */
@Configuration
public class WebSocketSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain webSocketSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/ws/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
