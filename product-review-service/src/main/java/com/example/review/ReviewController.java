package com.example.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewModelAssembler assembler;

    @GetMapping
    public CollectionModel<EntityModel<Review>> list(@RequestParam String productId) {
        return assembler.toCollectionModel(reviewService.listByProduct(productId));
    }

    @GetMapping("/summary")
    public ReviewSummary summary(@RequestParam String productId) {
        return reviewService.summarize(productId);
    }

    public record CreateReviewRequest(@NotBlank String productId, @Min(1) @Max(5) int rating, String body) {
    }

    @PostMapping
    public ResponseEntity<EntityModel<Review>> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateReviewRequest request) {
        String authorName = jwt.getClaimAsString("preferred_username");
        Review created = reviewService.create(request.productId(), jwt.getSubject(), authorName, request.rating(), request.body());
        return ResponseEntity.status(201).body(assembler.toModel(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        boolean isAdmin = hasAdminRole(jwt);
        reviewService.delete(id, jwt.getSubject(), isAdmin);
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
