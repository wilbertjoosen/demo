package com.example.user.controller;

import com.example.user.dto.request.AdminUpdateUserRequest;
import com.example.user.dto.request.UpdateProfileRequest;
import com.example.user.dto.request.CreateUserRequest;
import com.example.user.model.*;
import com.example.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserModelAssembler assembler;

    @GetMapping("/me")
    public EntityModel<UserProfileView> me(@AuthenticationPrincipal Jwt jwt) {
        return assembler.toModel(userService.getOrRegister(jwt));
    }

    @PutMapping("/me")
    public EntityModel<UserProfileView> updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        UserProfileView updated = userService.updateProfile(jwt, request.toFields());
        return assembler.toModel(updated);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CollectionModel<EntityModel<UserProfileView>> list() {
        return assembler.toCollectionModel(userService.list());
    }

    /** Realm roles an admin can pick from on the create/edit user form — read live from Keycloak. */
    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RealmRole> roles() {
        return userService.assignableRoles();
    }

    /** The realm roles a specific user currently holds — pre-fills the edit form. */
    @GetMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public List<String> userRoles(@PathVariable String id) {
        return userService.currentRoles(id);
    }

    /** Any authenticated user — a minimal, PII-free directory for picking someone to message. */
    @GetMapping("/directory")
    public List<UserDirectoryEntry> directory(@AuthenticationPrincipal Jwt jwt) {
        return userService.directory().stream()
                .filter(entry -> !entry.keycloakId().equals(jwt.getSubject()))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EntityModel<UserProfileView> getById(@PathVariable String id) {
        return assembler.toModel(userService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EntityModel<UserProfileView> updateUser(@PathVariable String id, @Valid @RequestBody AdminUpdateUserRequest request) {
        UserProfileView updated = userService.updateUser(id, request.toFields(), request.toIdentity(), request.roles());
        return assembler.toModel(updated);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EntityModel<UserProfileView>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserProfileView created = userService.createUser(request.username(), request.email(), request.firstName(),
                request.lastName(), request.password(), request.toFields(), request.rolesOrEmpty());
        EntityModel<UserProfileView> model = assembler.toModel(created);
        return ResponseEntity.created(URI.create(model.getRequiredLink("self").getHref())).body(model);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        userService.delete(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
