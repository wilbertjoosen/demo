package com.example.user;

/** Minimal, non-admin-gated projection of a user — just enough to pick a chat recipient, no PII. */
public record UserDirectoryEntry(String keycloakId, String username) {
}
