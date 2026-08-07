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

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public User getOrRegister(Jwt jwt) {
        return userRepository.findByKeycloakId(jwt.getSubject())
                .orElseGet(() -> registerFromToken(jwt));
    }

    @Override
    public User updateProfile(Jwt jwt, ProfileFields fields) {
        User user = getOrRegister(jwt);
        applyProfileFields(user, fields);
        return userRepository.save(user);
    }

    @Override
    public User createUser(String keycloakId, String username, String email, ProfileFields fields) {
        User user = new User(keycloakId, username, email, fields.displayName());
        applyProfileFields(user, fields);
        return userRepository.save(user);
    }

    private void applyProfileFields(User user, ProfileFields fields) {
        if (fields.displayName() != null) {
            user.setDisplayName(fields.displayName());
        }
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
    public List<User> list() {
        return userRepository.findByDeletedFalse();
    }

    @Override
    public User getById(String id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Override
    public void delete(String id) {
        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        user.markDeleted();
        userRepository.save(user);
    }

    private User registerFromToken(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        User user = userRepository.save(new User(jwt.getSubject(), username, email, username));
        kafkaTemplate.send(Topics.USER_EVENTS, DomainEvent.of(EventTypes.USER_REGISTERED, null, Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() == null ? "" : user.getEmail()
        )));
        return user;
    }
}
