package com.example.user.service;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.user.model.IdentityFields;
import com.example.user.model.KeycloakUserSummary;
import com.example.user.model.ProfileFields;
import com.example.user.model.User;
import com.example.user.model.UserDirectoryEntry;
import com.example.user.model.UserProfileView;
import com.example.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public UserProfileView getOrRegister(Jwt jwt) {
        User user = userRepository.findByKeycloakId(jwt.getSubject())
                .orElseGet(() -> registerFromToken(jwt));
        return UserProfileView.merge(user, identityFromJwt(jwt));
    }

    @Override
    public UserProfileView updateProfile(Jwt jwt, ProfileFields fields) {
        User user = userRepository.findByKeycloakId(jwt.getSubject())
                .orElseGet(() -> registerFromToken(jwt));
        applyProfileFields(user, fields);
        user = saveChecked(user);
        return UserProfileView.merge(user, identityFromJwt(jwt));
    }

    @Override
    public UserProfileView createUser(String username, String email, String firstName, String lastName,
                                      String password, ProfileFields fields, List<String> roles) {
        String keycloakId = keycloakAdminClient.createUser(username, email, firstName, lastName, password);
        try {
            keycloakAdminClient.assignRealmRoles(keycloakId, roles);
            User user = new User(keycloakId);
            applyProfileFields(user, fields);
            user = saveChecked(user);
            return UserProfileView.merge(user, new KeycloakUserSummary(keycloakId, username, email, firstName, lastName));
        } catch (RuntimeException ex) {
            // The Keycloak account is created but roles/profile aren't — undo it so a retry is clean.
            keycloakAdminClient.deleteUserQuietly(keycloakId);
            throw ex;
        }
    }

    @Override
    public List<com.example.user.model.RealmRole> assignableRoles() {
        return keycloakAdminClient.listAssignableRealmRoles();
    }

    @Override
    public List<String> currentRoles(String id) {
        return keycloakAdminClient.currentRealmRoleNames(findMongoUser(id).getKeycloakId());
    }

    private void applyProfileFields(User user, ProfileFields fields) {
        if (fields.shippingAddress() != null) {
            user.setShippingAddress(fields.shippingAddress());
        }
        if (fields.nationalId() != null) {
            assertNationalIdAvailable(user, fields.nationalId());
            user.setNationalId(fields.nationalId());
        }
        if (fields.phone() != null) {
            user.setPhone(fields.phone());
        }
        if (fields.customAttributes() != null) {
            user.setCustomAttributes(fields.customAttributes());
        }
    }

    /**
     * Friendly 409 before touching the DB. Not authoritative on its own — two concurrent requests
     * can both pass this — the partial unique index on {@code users.nationalId} (see
     * {@code UserIndexConfig}) is the real guard, surfaced as a 409 by {@link #saveChecked}.
     */
    private void assertNationalIdAvailable(User user, String candidate) {
        if (candidate.equals(user.getNationalId())) {
            return; // unchanged — a re-submit of the same value is not a conflict
        }
        userRepository.findByNationalIdAndDeletedFalse(candidate).ifPresent(owner -> {
            if (!owner.getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "National ID is already in use");
            }
        });
    }

    private User saveChecked(User user) {
        try {
            return userRepository.save(user);
        } catch (DuplicateKeyException e) {
            // Lost the race with a concurrent writer between assertNationalIdAvailable and here.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "National ID is already in use", e);
        }
    }

    /**
     * Keycloak is the source of truth for who exists, so the admin list is driven by it — not by
     * this service's Mongo store, which only gains a row when a user first signs in
     * ({@link #getOrRegister}). Anyone in the realm who has never used this app is backfilled with
     * an empty local profile here, so (a) they show up and (b) admin edit/delete — which key off
     * the Mongo id — work for them. After one call the two stores are in step.
     */
    @Override
    public List<UserProfileView> list() {
        Map<String, User> byKeycloakId = userRepository.findByDeletedFalse().stream()
                .collect(Collectors.toMap(User::getKeycloakId, u -> u, (a, b) -> a));

        List<KeycloakUserSummary> identities = keycloakAdminClient.listUsers();

        List<User> missing = identities.stream()
                .filter(identity -> !byKeycloakId.containsKey(identity.id()))
                .map(identity -> new User(identity.id()))
                .toList();
        if (!missing.isEmpty()) {
            // No USER_REGISTERED event — these accounts already exist, this is a local backfill,
            // not a new registration (reporting-service would otherwise double-count them).
            userRepository.saveAll(missing).forEach(u -> byKeycloakId.put(u.getKeycloakId(), u));
        }

        return identities.stream()
                .map(identity -> UserProfileView.merge(byKeycloakId.get(identity.id()), identity))
                .toList();
    }

    @Override
    public List<UserDirectoryEntry> directory() {
        Map<String, KeycloakUserSummary> identitiesByKeycloakId = new HashMap<>();
        for (KeycloakUserSummary identity : keycloakAdminClient.listUsers()) {
            identitiesByKeycloakId.put(identity.id(), identity);
        }
        return userRepository.findByDeletedFalse().stream()
                .map(user -> {
                    KeycloakUserSummary identity = identitiesByKeycloakId.get(user.getKeycloakId());
                    String username = identity == null ? "(unknown)" : identity.username();
                    return new UserDirectoryEntry(user.getKeycloakId(), username);
                })
                .toList();
    }

    @Override
    public UserProfileView getById(String id) {
        User user = findMongoUser(id);
        return UserProfileView.merge(user, keycloakAdminClient.findUser(user.getKeycloakId()).orElse(null));
    }

    @Override
    public UserProfileView updateUser(String id, ProfileFields fields, IdentityFields identity, List<String> roles) {
        User user = findMongoUser(id);
        applyProfileFields(user, fields);
        user = saveChecked(user);
        if (roles != null) {
            keycloakAdminClient.syncRealmRoles(user.getKeycloakId(), roles);
        }
        if (identity != null) {
            keycloakAdminClient.updateUser(user.getKeycloakId(), identity.username(), identity.email(),
                    identity.firstName(), identity.lastName());
            return UserProfileView.merge(user, new KeycloakUserSummary(user.getKeycloakId(), identity.username(),
                    identity.email(), identity.firstName(), identity.lastName()));
        }
        return UserProfileView.merge(user, keycloakAdminClient.findUser(user.getKeycloakId()).orElse(null));
    }

    @Override
    public void delete(String id, String requesterKeycloakId) {
        User user = findMongoUser(id);
        if (user.getKeycloakId().equals(requesterKeycloakId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot delete your own account");
        }
        user.markDeleted();
        userRepository.save(user);
    }

    private User findMongoUser(String id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private User registerFromToken(Jwt jwt) {
        User user = userRepository.save(new User(jwt.getSubject()));
        kafkaTemplate.send(Topics.USER_EVENTS, DomainEvent.of(EventTypes.USER_REGISTERED, null, Map.of(
                "userId", user.getId(),
                "username", jwt.getClaimAsString("preferred_username"),
                "email", jwt.getClaimAsString("email") == null ? "" : Objects.requireNonNull(jwt.getClaimAsString("email"))
        )));
        return user;
    }

    private KeycloakUserSummary identityFromJwt(Jwt jwt) {
        return new KeycloakUserSummary(jwt.getSubject(), jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"), jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"));
    }
}
