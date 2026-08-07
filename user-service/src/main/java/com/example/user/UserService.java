package com.example.user;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface UserService {

    UserProfileView getOrRegister(Jwt jwt);

    UserProfileView updateProfile(Jwt jwt, ProfileFields fields);

    /** Admin-only: provisions a real Keycloak account via the Admin REST API, then a linked local profile. */
    UserProfileView createUser(String username, String email, String firstName, String lastName, String password, ProfileFields fields);

    List<UserProfileView> list();

    /** Any authenticated user, not just admins — used to populate the "start a new chat" picker. */
    List<UserDirectoryEntry> directory();

    UserProfileView getById(String id);

    /**
     * Admin-only edit of another user's profile. {@code identity}, if non-null, also updates
     * username/email/name directly in Keycloak — self-service {@link #updateProfile} can't do this,
     * only an admin editing someone else's record can.
     */
    UserProfileView updateUser(String id, ProfileFields fields, IdentityFields identity);

    /**
     * Soft delete — marks the profile instead of removing it. Does not affect the Keycloak account or login.
     * {@code requesterKeycloakId} guards against an admin deleting their own profile, which would leave them
     * locked out of the admin UI (their own row would vanish) with no other admin necessarily available to fix it.
     */
    void delete(String id, String requesterKeycloakId);
}
