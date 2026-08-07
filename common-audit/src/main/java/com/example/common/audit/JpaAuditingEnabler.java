package com.example.common.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@code auditorAwareRef} must be {@link JwtAuditorAware}'s fully-qualified class name, not
 * "jwtAuditorAware": beans registered via {@code @Import} of a plain (non-scanned) class get the
 * FQCN as their bean name, not the usual decapitalized-simple-name — unlike component-scanned
 * beans, which is what every other cross-module bean here relies on (wired by type, never by name,
 * so the naming difference never mattered until this by-name lookup).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "com.example.common.audit.JwtAuditorAware")
public class JpaAuditingEnabler {
}
