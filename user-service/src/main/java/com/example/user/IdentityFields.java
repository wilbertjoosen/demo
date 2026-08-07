package com.example.user;

/** Admin-only: username/email/name changes go straight to Keycloak, the source of truth for identity. */
record IdentityFields(String username, String email, String firstName, String lastName) {
}
