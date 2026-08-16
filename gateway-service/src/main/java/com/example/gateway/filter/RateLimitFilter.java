package com.example.gateway.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client-IP request throttling at the edge, applied globally to every route rather than
 * per-route YAML config — this gateway build (spring-cloud-gateway-server-webmvc) has no
 * "default-filters" concept, and its declarative Bucket4jFilterFunctions.rateLimit() shortcut
 * needs a distributed AsyncProxyManager bean (e.g. Redis-backed) that would be overkill for a
 * single-replica gateway; a plain in-memory bucket per key is the right fit here. Swap to
 * bucket4j-redis + a shared AsyncProxyManager if this gateway is ever scaled to multiple replicas,
 * since separate in-memory buckets per instance would let a client exceed the intended limit by
 * spreading requests across instances.
 *
 * <p>The per-IP bucket map is never evicted — acceptable at this project's scale (a handful of
 * distinct client IPs, short-lived local/demo runs), not something to build out for a real
 * production deployment without an eviction policy.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long capacity;
    private final long refillTokens;
    private final Duration refillPeriod;
    private final Set<String> allowedOrigins;

    public RateLimitFilter(
            @Value("${rate-limit.capacity}") long capacity,
            @Value("${rate-limit.refill-tokens}") long refillTokens,
            @Value("${rate-limit.refill-period-seconds}") long refillPeriodSeconds,
            @Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriod = Duration.ofSeconds(refillPeriodSeconds);
        this.allowedOrigins = Set.copyOf(Arrays.asList(allowedOrigins));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Health/metrics traffic (k8s probes, in-cluster Prometheus scraping every 15s) must never
        // be throttled — that would turn a rate limit into a self-inflicted liveness-probe failure.
        // CORS preflight (OPTIONS) requests are browser-initiated plumbing, not real backend calls —
        // exempting them avoids a client's own preflight traffic eating into its own request budget.
        return request.getRequestURI().startsWith("/actuator/") || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(clientKey(request), key -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(refillTokens, refillPeriod).build())
                .build());

        if (bucket.tryConsume(1)) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        // Rejected requests short-circuit before DispatcherServlet, which is where CORS headers are
        // normally added (CorsConfig is handler-mapping-based, not a separate filter) — without
        // this, the browser would surface a same-origin CORS failure instead of the actual 429,
        // and the frontend's error handling would never see the real status/body. The header value
        // written back is the matching entry from the trusted allowedOrigins config, not the raw
        // request header, so this never echoes unvalidated request input into a response header.
        String requestOrigin = request.getHeader("Origin");
        allowedOrigins.stream().filter(allowed -> allowed.equals(requestOrigin)).findFirst().ifPresent(origin -> {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
        });
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(refillPeriod.toSeconds()));
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":429,\"message\":\"Too many requests — please slow down.\"}");
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
