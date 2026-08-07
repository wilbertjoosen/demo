package com.example.user;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class UserServiceImpl implements UserService {

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
        user = userRepository.save(user);
        return UserProfileView.merge(user, identityFromJwt(jwt));
    }

    @Override
    public UserProfileView createUser(String username, String email, String firstName, String lastName, String password, ProfileFields fields) {
        String keycloakId = keycloakAdminClient.createUser(username, email, firstName, lastName, password);
        User user = new User(keycloakId);
        applyProfileFields(user, fields);
        user = userRepository.save(user);
        return UserProfileView.merge(user, new KeycloakUserSummary(keycloakId, username, email, firstName, lastName));
    }

    private void applyProfileFields(User user, ProfileFields fields) {
        if (fields.shippingAddress() != null) {
            user.setShippingAddress(fields.shippingAddress());
        }
        if (fields.nationalId() != null) {
            user.setNationalId(fields.nationalId());
        }
        if (fields.phone() != null) {
            user.setPhone(fields.phone());
        }
        if (fields.customAttributes() != null) {
            user.setCustomAttributes(fields.customAttributes());
        }
    }

    @Override
    public List<UserProfileView> list() {
        List<User> users = userRepository.findByDeletedFalse();
        Map<String, KeycloakUserSummary> identitiesByKeycloakId = new HashMap<>();
        for (KeycloakUserSummary identity : keycloakAdminClient.listUsers()) {
            identitiesByKeycloakId.put(identity.id(), identity);
        }
        return users.stream()
                .map(user -> UserProfileView.merge(user, identitiesByKeycloakId.get(user.getKeycloakId())))
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
    public UserProfileView updateUser(String id, ProfileFields fields, IdentityFields identity) {
        User user = findMongoUser(id);
        applyProfileFields(user, fields);
        user = userRepository.save(user);
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
                "email", jwt.getClaimAsString("email") == null ? "" : jwt.getClaimAsString("email")
        )));
        return user;
    }

    private KeycloakUserSummary identityFromJwt(Jwt jwt) {
        return new KeycloakUserSummary(jwt.getSubject(), jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"), jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"));
    }
}
