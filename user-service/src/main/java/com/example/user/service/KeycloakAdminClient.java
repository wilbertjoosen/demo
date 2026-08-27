package com.example.user.service;

import com.example.user.model.KeycloakUserSummary;
import com.example.user.model.RealmRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to Keycloak's own Admin REST API so username/email/firstName/lastName are read live from
 * Keycloak rather than duplicated (and allowed to drift) in this service's Mongo store. Authenticated
 * via client_credentials (see KeycloakAdminClientConfig), same pattern as order-service calling
 * inventory-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KeycloakUserRepresentation(String id, String username, String email, String firstName, String lastName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KeycloakRoleRepresentation(String id, String name, String description) {
    }

    /** Keycloak's own composite/technical realm roles — never offered as an assignable choice. */
    private static final Set<String> BUILTIN_ROLES =
            Set.of("offline_access", "uma_authorization", "default-roles-demo", "default-roles-demo-qa");

    /** Keycloak error bodies: {"errorMessage":"..."} on the admin API, {"error":..,"error_description":..} on token flows. */
    private static final Pattern KEYCLOAK_ERROR =
            Pattern.compile("\"(?:errorMessage|error_description|error)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final RestClient keycloakAdminRestClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public List<KeycloakUserSummary> listUsers() {
        KeycloakUserRepresentation[] users = keycloakAdminRestClient.get()
                .uri("/users?max=1000")
                .headers(h -> h.setBearerAuth(fetchAccessToken()))
                .retrieve()
                .body(KeycloakUserRepresentation[].class);
        return users == null ? List.of() : List.of(users).stream().map(this::toSummary).toList();
    }

    public Optional<KeycloakUserSummary> findUser(String keycloakId) {
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
    public String createUser(String username, String email, String firstName, String lastName, String password) {
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of("type", "password", "value", password, "temporary", false))
        );
        URI location;
        try {
            location = keycloakAdminRestClient.post()
                    .uri("/users")
                    .headers(h -> h.setBearerAuth(fetchAccessToken()))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();
        } catch (HttpClientErrorException e) {
            // e.g. 409 "User exists with same username" / "same email", 400 password-policy messages
            throw translate(e);
        }
        if (location == null) {
            throw new IllegalStateException("Keycloak did not return a Location header for the created user");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Realm roles an admin may assign, Keycloak's built-in composite/technical roles filtered out. */
    public List<RealmRole> listAssignableRealmRoles() {
        KeycloakRoleRepresentation[] roles;
        try {
            roles = keycloakAdminRestClient.get()
                    .uri("/roles")
                    .headers(h -> h.setBearerAuth(fetchAccessToken()))
                    .retrieve()
                    .body(KeycloakRoleRepresentation[].class);
        } catch (HttpClientErrorException e) {
            throw translate(e);
        }
        if (roles == null) {
            return List.of();
        }
        return Arrays.stream(roles)
                .filter(r -> r.name() != null && !BUILTIN_ROLES.contains(r.name()))
                .map(r -> new RealmRole(r.name(), r.description()))
                .toList();
    }

    /**
     * Assigns the given realm roles to a user. No-op for an empty list. An unknown role name is a
     * 400 — the caller (an admin form) picked from this same list, so it shouldn't happen.
     */
    public void assignRealmRoles(String keycloakId, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return;
        }
        KeycloakRoleRepresentation[] all;
        try {
            all = keycloakAdminRestClient.get()
                    .uri("/roles")
                    .headers(h -> h.setBearerAuth(fetchAccessToken()))
                    .retrieve()
                    .body(KeycloakRoleRepresentation[].class);
        } catch (HttpClientErrorException e) {
            throw translate(e);
        }
        Map<String, KeycloakRoleRepresentation> byName = all == null ? Map.of()
                : Arrays.stream(all).collect(java.util.stream.Collectors.toMap(KeycloakRoleRepresentation::name, r -> r, (a, b) -> a));

        List<Map<String, String>> payload = roleNames.stream().distinct().map(name -> {
            KeycloakRoleRepresentation role = byName.get(name);
            if (role == null || BUILTIN_ROLES.contains(name)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown realm role: " + name);
            }
            return Map.of("id", role.id(), "name", role.name());
        }).toList();

        try {
            keycloakAdminRestClient.post()
                    .uri("/users/{id}/role-mappings/realm", keycloakId)
                    .headers(h -> h.setBearerAuth(fetchAccessToken()))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw translate(e);
        }
    }

    /** Best-effort cleanup — used to roll back a half-provisioned account. Never throws. */
    public void deleteUserQuietly(String keycloakId) {
        try {
            keycloakAdminRestClient.delete()
                    .uri("/users/{id}", keycloakId)
                    .headers(h -> h.setBearerAuth(fetchAccessToken()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Failed to roll back Keycloak user {} after a provisioning error: {}", keycloakId, e.toString());
        }
    }

    /** Surface Keycloak's own error message (and status) to the client instead of a generic 500. */
    private ResponseStatusException translate(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        Matcher m = KEYCLOAK_ERROR.matcher(body);
        String message = m.find() ? m.group(1).replace("\\\"", "\"") : e.getStatusText();
        return new ResponseStatusException(e.getStatusCode(), message);
    }

    /**
     * Fetch-merge-PUT: Keycloak's PUT /users/{id} replaces the whole representation, so blindly
     * sending just {username, email, firstName, lastName} would wipe out enabled/requiredActions/etc.
     * for the account. Fetching the current representation as a raw Map (not the trimmed
     * KeycloakUserRepresentation record above) and only overwriting the identity fields keeps
     * everything else Keycloak already has on the account intact.
     */
    @SuppressWarnings("unchecked")
    public void updateUser(String keycloakId, String username, String email, String firstName, String lastName) {
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
