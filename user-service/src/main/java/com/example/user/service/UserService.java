package com.example.user.service;
import com.example.user.model.IdentityFields;
import com.example.user.model.ProfileFields;
import com.example.user.model.RealmRole;
import com.example.user.model.UserDirectoryEntry;
import com.example.user.model.UserProfileView;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface UserService {

    UserProfileView getOrRegister(Jwt jwt);

    UserProfileView updateProfile(Jwt jwt, ProfileFields fields);

    /**
     * Admin-only: provisions a real Keycloak account via the Admin REST API, assigns {@code roles}
     * (empty = realm default only), then a linked local profile. Rolls the Keycloak account back if
     * role assignment or the local save fails.
     */
    UserProfileView createUser(String username, String email, String firstName, String lastName, String password,
                               ProfileFields fields, List<String> roles);

    /** Realm roles an admin may pick from when creating a user — read live from Keycloak. */
    List<RealmRole> assignableRoles();

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
