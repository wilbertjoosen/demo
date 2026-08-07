package com.example.common.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Shared {@code createdBy}/{@code lastModifiedBy} source for both JPA (@EnableJpaAuditing) and
 * Mongo (@EnableMongoAuditing) auditing. Falls back to "system" — not "anonymous" — because saga
 * listeners mutate entities from Kafka consumer threads, which have no SecurityContext at all.
 */
@Component
public class JwtAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.of("system");
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            return Optional.of(username != null ? username : jwt.getSubject());
        }
        return Optional.of(auth.getName());
    }
}
