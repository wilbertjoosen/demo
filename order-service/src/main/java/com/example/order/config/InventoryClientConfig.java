package com.example.order.config;
import com.example.order.service.InventoryServiceClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * order-service authenticates its own call to inventory-service via client_credentials
 * (client "order-service-client" in Keycloak) rather than relaying the end-user's token — this
 * keeps the call working even for saga steps with no HTTP request/user in context.
 *
 * <p>Deliberately NOT using {@code ServletOAuth2AuthorizedClientExchangeFilterFunction}: it's built
 * around an active servlet request (real bug found during testing — it silently failed to attach a
 * token when called from a Kafka listener thread, which has no HttpServletRequest in scope, and the
 * circuit breaker's fallback swallowed the resulting error with no log trace). InventoryServiceClient
 * instead calls {@link OAuth2AuthorizedClientManager#authorize} directly and sets the header itself,
 * which works identically from any thread.
 */
@Configuration
public class InventoryClientConfig {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                                   OAuth2AuthorizedClientService authorizedClientService) {
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    public WebClient inventoryServiceWebClient(WebClient.Builder builder,
                                                @Value("${services.inventory-service.uri}") String inventoryServiceUri) {
        return builder.baseUrl(inventoryServiceUri).build();
    }
}
