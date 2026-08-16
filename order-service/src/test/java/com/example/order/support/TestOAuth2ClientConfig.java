package com.example.order.support;


import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;

/**
 * Replaces the real client_credentials handshake (InventoryClientConfig) with a fixed token, so
 * these tests exercise order-service's own logic and its real HTTP call to inventory-service
 * (stubbed via WireMock) without needing a live Keycloak just to mint a token. Scope: this is
 * deliberately NOT testing Spring Security's OAuth2 client module, which is framework-tested already.
 */
@TestConfiguration
public class TestOAuth2ClientConfig {

    public static final String FAKE_TOKEN = "test-inventory-service-token";

    @Bean
    @Primary
    public OAuth2AuthorizedClientManager testAuthorizedClientManager(ClientRegistrationRepository clientRegistrationRepository) {
        return request -> new OAuth2AuthorizedClient(
                clientRegistrationRepository.findByRegistrationId("inventory-service"),
                "order-service",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, FAKE_TOKEN, Instant.now(), Instant.now().plusSeconds(3600)));
    }
}
