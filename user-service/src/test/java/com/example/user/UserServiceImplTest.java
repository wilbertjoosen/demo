package com.example.user;

import com.example.common.events.DomainEvent;
import com.example.common.events.EventContracts;
import com.example.common.events.EventTypes;
import com.example.common.events.Topics;
import com.example.user.model.User;
import com.example.user.model.UserProfileView;
import com.example.user.repository.UserRepository;
import com.example.user.service.KeycloakAdminClient;
import com.example.user.service.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    UserRepository userRepository;
    @Mock
    KeycloakAdminClient keycloakAdminClient;
    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, keycloakAdminClient, kafkaTemplate);
    }

    private Jwt jwt(String subject, String username, String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", username)
                .claim("email", email)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private User withId(User user, String id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    @Test
    void getOrRegister_firstLogin_registersUserAndPublishesUserRegistered() {
        Jwt token = jwt("sub-1", "alice", "alice@example.com");
        when(userRepository.findByKeycloakId("sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), "u1"));

        UserProfileView result = userService.getOrRegister(token);

        assertThat(result.id()).isEqualTo("u1");
        assertThat(result.keycloakId()).isEqualTo("sub-1");
        assertThat(result.username()).isEqualTo("alice");
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(kafkaTemplate).send(eq(Topics.USER_EVENTS), eventCaptor.capture());
        DomainEvent published = eventCaptor.getValue();
        assertThat(published.eventType()).isEqualTo(EventTypes.USER_REGISTERED);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) published.payload();
        assertThat(payload).containsEntry("userId", "u1").containsEntry("username", "alice");
        assertThat(EventContracts.missingFields(EventTypes.USER_REGISTERED, payload)).isEmpty();
    }

    @Test
    void getOrRegister_existingUser_doesNotRegisterAgain() {
        Jwt token = jwt("sub-1", "alice", "alice@example.com");
        User existing = withId(new User("sub-1"), "u1");
        when(userRepository.findByKeycloakId("sub-1")).thenReturn(Optional.of(existing));

        UserProfileView result = userService.getOrRegister(token);

        assertThat(result.id()).isEqualTo("u1");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void delete_ownAccount_throwsConflict() {
        User user = withId(new User("sub-1"), "u1");
        when(userRepository.findByIdAndDeletedFalse("u1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.delete("u1", "sub-1")).isInstanceOf(ResponseStatusException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void delete_otherAccount_marksDeleted() {
        User user = withId(new User("sub-1"), "u1");
        when(userRepository.findByIdAndDeletedFalse("u1")).thenReturn(Optional.of(user));

        userService.delete("u1", "sub-admin");

        assertThat(user.isDeleted()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void delete_notFound_throwsNotFound() {
        when(userRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete("missing", "sub-admin")).isInstanceOf(ResponseStatusException.class);
    }
}
