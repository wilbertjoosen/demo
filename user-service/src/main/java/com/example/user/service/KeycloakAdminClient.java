package com.example.user.service;

import com.example.user.model.KeycloakUserSummary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Talks to Keycloak's own Admin REST API so username/email/firstName/lastName are read live from
 * Keycloak rather than duplicated (and allowed to drift) in this service's Mongo store. Authenticated
 * via client_credentials (see KeycloakAdminClientConfig), same pattern as order-service calling
 * inventory-service.
 */
@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KeycloakUserRepresentation(String id, String username, String email, String firstName, String lastName) {
    }

    private final RestClient keycloakAdminRestClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    List<KeycloakUserSummary> listUsers() {
        KeycloakUserRepresentation[] users = keycloakAdminRestClient.get()
                .uri("/users?max=1000")
                .headers(h -> h.setBearerAuth(fetchAccessToken()))
                .retrieve()
                .body(KeycloakUserRepresentation[].class);
        return users == null ? List.of() : List.of(users).stream().map(this::toSummary).toList();
    }

    Optional<KeycloakUserSummary> findUser(String keycloakId) {
        try {
            KeycloakUserRepresentation user = keycloakAdminRestClient.get()
                    .uri("/users/{id}", keycloakId)
                    .headers(h -> h.setBearerAuth(fetchAccessToken()))
                    .retrieve()
                    .body(KeycloakUserRepresentation.class);
            return Optional.ofNullable(user).map(this::toSummary);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    /** Creates a real Keycloak account (enabled, email pre-verified, given temporary-false password) and returns its id. */
    String createUser(String username, String email, String firstName, String lastName, String password) {
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of("type", "password", "value", password, "temporary", false))
        );
        URI location = keycloakAdminRestClient.post()
                .uri("/users")
                .headers(h -> h.setBearerAuth(fetchAccessToken()))
                .body(body)
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation();
        if (location == null) {
            throw new IllegalStateException("Keycloak did not return a Location header for the created user");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Fetch-merge-PUT: Keycloak's PUT /users/{id} replaces the whole representation, so blindly
     * sending just {username, email, firstName, lastName} would wipe out enabled/requiredActions/etc.
     * for the account. Fetching the current representation as a raw Map (not the trimmed
     * KeycloakUserRepresentation record above) and only overwriting the identity fields keeps
     * everything else Keycloak already has on the account intact.
     */
    @SuppressWarnings("unchecked")
    void updateUser(String keycloakId, String username, String email, String firstName, String lastName) {
        Map<String, Object> current = keycloakAdminRestClient.get()
                .uri("/users/{id}", keycloakId)
                .headers(h -> h.setBearerAuth(fetchAccessToken()))
                .retrieve()
                .body(Map.class);
        if (current == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        current.put("username", username);
        current.put("email", email);
        current.put("firstName", firstName);
        current.put("lastName", lastName);
        keycloakAdminRestClient.put()
                .uri("/users/{id}", keycloakId)
                .headers(h -> h.setBearerAuth(fetchAccessToken()))
                .body(current)
                .retrieve()
                .toBodilessEntity();
    }

    private KeycloakUserSummary toSummary(KeycloakUserRepresentation r) {
        return new KeycloakUserSummary(r.id(), r.username(), r.email(), r.firstName(), r.lastName());
    }

    private String fetchAccessToken() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId("keycloak-admin")
                .principal("user-service")
                .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(request);
        if (authorizedClient == null) {
            throw new IllegalStateException("Could not authorize client_credentials for keycloak-admin");
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }
}
