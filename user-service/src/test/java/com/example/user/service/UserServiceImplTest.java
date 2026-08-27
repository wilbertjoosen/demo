package com.example.user.service;

import com.example.user.model.KeycloakUserSummary;
import com.example.user.model.ProfileFields;
import com.example.user.model.User;
import com.example.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void createUser_assignsRolesThenSaves() {
        when(keycloakAdminClient.createUser("u", "u@x.io", "F", "L", "pw")).thenReturn("kc-1");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createUser("u", "u@x.io", "F", "L", "pw", fields(null), List.of("admin", "finance"));

        verify(keycloakAdminClient).assignRealmRoles("kc-1", List.of("admin", "finance"));
        verify(userRepository).save(any(User.class));
        verify(keycloakAdminClient, never()).deleteUserQuietly(any());
    }

    @Test
    void createUser_rollsBackKeycloakAccountWhenRoleAssignmentFails() {
        when(keycloakAdminClient.createUser(any(), any(), any(), any(), any())).thenReturn("kc-1");
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown realm role: bogus"))
                .when(keycloakAdminClient).assignRealmRoles(eq("kc-1"), anyList());

        assertThatThrownBy(() -> service.createUser("u", "u@x.io", "F", "L", "pw", fields(null), List.of("bogus")))
                .isInstanceOf(ResponseStatusException.class);

        verify(keycloakAdminClient).deleteUserQuietly("kc-1");
        verify(userRepository, never()).save(any());
    }

    @Test
    void list_backfillsLocalRowsForKeycloakOnlyUsers() {
        User known = userWithId("m1", "kc-known");
        when(userRepository.findByDeletedFalse()).thenReturn(List.of(known));
        when(keycloakAdminClient.listUsers()).thenReturn(List.of(
                new KeycloakUserSummary("kc-known", "known", "k@x.io", "K", "N"),
                new KeycloakUserSummary("kc-new", "newbie", "n@x.io", "New", "Bie")));
        when(userRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.list();

        ArgumentCaptor<List<User>> saved = ArgumentCaptor.forClass(List.class);
        verify(userRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement()
                .satisfies(u -> assertThat(u.getKeycloakId()).isEqualTo("kc-new"));
        assertThat(result).extracting(v -> v.username()).containsExactlyInAnyOrder("known", "newbie");
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
