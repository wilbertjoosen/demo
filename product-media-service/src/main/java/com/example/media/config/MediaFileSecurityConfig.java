package com.example.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * <img>/<video> tags can't attach an Authorization header, so served files need to be publicly
 * readable — same rationale and @Order(1) pre-empting pattern as notification-service/chat-service's
 * WebSocketSecurityConfig. Uploading (POST /api/media/upload) stays behind the normal role-gated chain.
 */
@Configuration
public class MediaFileSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain mediaFileSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/media/files/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
