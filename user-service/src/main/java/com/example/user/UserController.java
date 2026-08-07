package com.example.user;

import com.example.common.model.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserModelAssembler assembler;

    @GetMapping("/me")
    public EntityModel<User> me(@AuthenticationPrincipal Jwt jwt) {
        return assembler.toModel(userService.getOrRegister(jwt));
    }

    public record UpdateProfileRequest(String displayName, @Valid Address shippingAddress, String nationalId,
                                        String phone, Map<String, String> customAttributes) {
        ProfileFields toFields() {
            return new ProfileFields(displayName, shippingAddress, nationalId, phone, customAttributes);
        }
    }

    @PutMapping("/me")
    public EntityModel<User> updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        User updated = userService.updateProfile(jwt, request.toFields());
        return assembler.toModel(updated);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CollectionModel<EntityModel<User>> list() {
        return assembler.toCollectionModel(userService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EntityModel<User> getById(@PathVariable String id) {
        return assembler.toModel(userService.getById(id));
    }

    public record CreateUserRequest(@NotBlank String keycloakId, @NotBlank String username, @NotBlank @Email String email,
                                     String displayName, @Valid Address shippingAddress, String nationalId, String phone,
                                     Map<String, String> customAttributes) {
        ProfileFields toFields() {
            return new ProfileFields(displayName, shippingAddress, nationalId, phone, customAttributes);
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EntityModel<User>> createUser(@Valid @RequestBody CreateUserRequest request) {
        User created = userService.createUser(request.keycloakId(), request.username(), request.email(), request.toFields());
        EntityModel<User> model = assembler.toModel(created);
        return ResponseEntity.created(URI.create(model.getRequiredLink("self").getHref())).body(model);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
