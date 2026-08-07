package com.example.comment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final CommentModelAssembler assembler;

    @GetMapping
    public CollectionModel<EntityModel<Comment>> list(@RequestParam String productId) {
        return assembler.toCollectionModel(commentService.listByProduct(productId));
    }

    public record CreateCommentRequest(@NotBlank String productId, String parentId, @NotBlank String body) {
    }

    @PostMapping
    public ResponseEntity<EntityModel<Comment>> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCommentRequest request) {
        String authorName = jwt.getClaimAsString("preferred_username");
        Comment created = commentService.create(request.productId(), request.parentId(), jwt.getSubject(), authorName, request.body());
        return ResponseEntity.status(201).body(assembler.toModel(created));
    }

    public record UpdateCommentRequest(@NotBlank String body) {
    }

    @PutMapping("/{id}")
    public EntityModel<Comment> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody UpdateCommentRequest request) {
        boolean isAdmin = hasAdminRole(jwt);
        Comment updated = commentService.update(id, jwt.getSubject(), isAdmin, request.body());
        return assembler.toModel(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        boolean isAdmin = hasAdminRole(jwt);
        commentService.delete(id, jwt.getSubject(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("unchecked")
    private boolean hasAdminRole(Jwt jwt) {
        var realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof java.util.Map<?, ?> map) || !(map.get("roles") instanceof Collection<?> roles)) {
            return false;
        }
        return roles.stream().anyMatch(r -> "admin".equalsIgnoreCase(String.valueOf(r)));
    }
}
