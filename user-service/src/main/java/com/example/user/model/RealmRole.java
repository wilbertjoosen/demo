package com.example.user.model;

/** A realm role an admin can assign when creating a user — read live from Keycloak. */
public record RealmRole(String name, String description) {
}
