package com.example.user.controller;

import com.example.user.model.IdentityFields;
import com.example.user.model.ProfileFields;
import com.example.user.model.UserDirectoryEntry;
import com.example.user.model.UserModelAssembler;
import com.example.user.model.UserProfileView;
import com.example.user.service.UserService;

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
import java.util.List;
import java.util.Map;

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

    public record UpdateProfileRequest(Address shippingAddress, String nationalId, String phone,
                                        Map<String, String> customAttributes) {
        ProfileFields toFields() {
            return new ProfileFields(blankToNull(shippingAddress), nationalId, phone, customAttributes);
        }
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

    public record AdminUpdateUserRequest(Address shippingAddress, String nationalId, String phone,
                                          Map<String, String> customAttributes, String username,
                                          @Email String email, String firstName, String lastName) {
        ProfileFields toFields() {
            return new ProfileFields(blankToNull(shippingAddress), nationalId, phone, customAttributes);
        }

        IdentityFields toIdentity() {
            if (username == null && email == null && firstName == null && lastName == null) {
                return null;
            }
            return new IdentityFields(username, email, firstName, lastName);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EntityModel<UserProfileView> updateUser(@PathVariable String id, @Valid @RequestBody AdminUpdateUserRequest request) {
        UserProfileView updated = userService.updateUser(id, request.toFields(), request.toIdentity());
        return assembler.toModel(updated);
    }

    public record CreateUserRequest(@NotBlank String username, @NotBlank @Email String email, @NotBlank String firstName,
                                     @NotBlank String lastName, @NotBlank String password, Address shippingAddress,
                                     String nationalId, String phone, Map<String, String> customAttributes) {
        ProfileFields toFields() {
            return new ProfileFields(blankToNull(shippingAddress), nationalId, phone, customAttributes);
        }
    }

    /** The frontend's optional-address forms submit an all-blank object rather than omitting the field. */
    private static Address blankToNull(Address address) {
        if (address == null) {
            return null;
        }
        boolean allBlank = isBlank(address.getStreet()) && isBlank(address.getCity())
                && isBlank(address.getPostalCode()) && isBlank(address.getCountry());
        return allBlank ? null : address;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EntityModel<UserProfileView>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserProfileView created = userService.createUser(request.username(), request.email(), request.firstName(),
                request.lastName(), request.password(), request.toFields());
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
