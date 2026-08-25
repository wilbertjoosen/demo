package com.example.common.security;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tags the current request's span with the OTel semantic convention {@code enduser.id} — the
 * authenticated user's Keycloak subject ({@code sub} claim) — so a trace can be found by which
 * user triggered it, not just by service/endpoint. Registered by {@link ResourceServerSecurityConfig}
 * to run immediately after JWT authentication, since that's the earliest point the principal exists.
 *
 * <p>No-ops (not an error) for unauthenticated or non-JWT requests, and for any request handled
 * before a span has actually started.
 */
public class EndUserIdTracingFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public EndUserIdTracingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
            Span span = tracer.currentSpan();
            if (span != null) {
                span.tag("enduser.id", jwtAuth.getToken().getSubject());
            }
        }
        filterChain.doFilter(request, response);
    }
}
