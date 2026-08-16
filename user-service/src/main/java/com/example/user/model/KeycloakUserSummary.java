package com.example.user.model;

/** Identity fields as Keycloak itself has them — the single source of truth this service no longer duplicates. */
public record KeycloakUserSummary(String id, String username, String email, String firstName, String lastName) {
}
