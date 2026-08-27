package com.example.user.service;

import com.example.user.model.ProfileFields;
import com.example.user.model.User;
import com.example.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String NATIONAL_ID = "12345678911";

    @Mock
    private UserRepository userRepository;
    @Mock
    private KeycloakAdminClient keycloakAdminClient;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserServiceImpl service;

    @Test
    void updateProfile_rejectsNationalIdOwnedByAnotherUser() {
        User me = userWithId("me", "kc-me");
        User other = userWithId("other", "kc-other");
        other.setNationalId(NATIONAL_ID);
        when(userRepository.findByKeycloakId("kc-me")).thenReturn(Optional.of(me));
        when(userRepository.findByNationalIdAndDeletedFalse(NATIONAL_ID)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateProfile(jwt("kc-me"), fields(NATIONAL_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_allowsResubmittingOwnNationalId() {
        User me = userWithId("me", "kc-me");
        me.setNationalId(NATIONAL_ID);
        when(userRepository.findByKeycloakId("kc-me")).thenReturn(Optional.of(me));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateProfile(jwt("kc-me"), fields(NATIONAL_ID));

        // Unchanged value short-circuits before any uniqueness query.
        verify(userRepository, never()).findByNationalIdAndDeletedFalse(any());
        verify(userRepository).save(me);
    }

    @Test
    void updateProfile_mapsDuplicateKeyFromSaveToConflict() {
        User me = userWithId("me", "kc-me");
        when(userRepository.findByKeycloakId("kc-me")).thenReturn(Optional.of(me));
        when(userRepository.findByNationalIdAndDeletedFalse(NATIONAL_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        assertThatThrownBy(() -> service.updateProfile(jwt("kc-me"), fields(NATIONAL_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateUser_rejectsNationalIdOwnedByAnotherUser() {
        User target = userWithId("target", "kc-target");
        User other = userWithId("other", "kc-other");
        other.setNationalId(NATIONAL_ID);
        when(userRepository.findByIdAndDeletedFalse("target")).thenReturn(Optional.of(target));
        when(userRepository.findByNationalIdAndDeletedFalse(NATIONAL_ID)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateUser("target", fields(NATIONAL_ID), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any());
    }

    private static User userWithId(String id, String keycloakId) {
        User user = new User(keycloakId);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static ProfileFields fields(String nationalId) {
        return new ProfileFields(null, nationalId, null, null);
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", "tester")
                .build();
    }
}
