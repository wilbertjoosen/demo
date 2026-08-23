package com.example.chat.support;


import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;

/**
 * DirectMessageHandshakeInterceptor decodes a real JWT itself (it's outside the servlet security
 * filter chain — see WebSocketSecurityConfig), so unlike the REST tests (which bypass decoding
 * entirely via SecurityMockMvcRequestPostProcessors.jwt()), the WebSocket tests need a JwtDecoder
 * that actually runs. Rather than standing up a real Keycloak just to mint signed tokens, this fake
 * treats the raw token string AS the subject/username — the token IS "user-1", "user-2", etc. — and
 * rejects one specific sentinel value so the invalid-token path is still exercisable.
 */
@TestConfiguration
public class TestJwtDecoderConfig {

    public static final String INVALID_TOKEN = "garbage-token";

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        return token -> {
            if (INVALID_TOKEN.equals(token) || token == null || token.isBlank()) {
                throw new JwtException("invalid test token");
            }
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(token)
                    .claim("preferred_username", token)
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .build();
        };
    }
}
