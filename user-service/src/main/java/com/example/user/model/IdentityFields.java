package com.example.user.model;

/** Admin-only: username/email/name changes go straight to Keycloak, the source of truth for identity. */
public record IdentityFields(String username, String email, String firstName, String lastName) {
}
