package com.example.user;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface UserService {

    User getOrRegister(Jwt jwt);

    User updateProfile(Jwt jwt, ProfileFields fields);

    /**
     * Admin-only, local-profile-only (no Keycloak account is created) — a deliberately simpler
     * choice than full provisioning via Keycloak's Admin REST API. Known limitation: if this
     * keycloakId doesn't match a real account (or is left null), the person can't actually log in
     * against this profile, and a real future login would just create a separate record via
     * getOrRegister rather than claiming this one.
     */
    User createUser(String keycloakId, String username, String email, ProfileFields fields);

    List<User> list();

    User getById(String id);

    /** Soft delete — marks the profile instead of removing it. Does not affect the Keycloak account or login. */
    void delete(String id);
}
