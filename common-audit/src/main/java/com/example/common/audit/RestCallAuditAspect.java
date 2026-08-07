package com.example.common.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cross-cutting activity log: every @RestController method call becomes an AuditEvent, with no
 * per-endpoint instrumentation needed in any of the 9 services. Pointcut matches any public method
 * on a class annotated @RestController — i.e. every REST endpoint, automatically.
 */
@Aspect
@Component
@Slf4j
public class RestCallAuditAspect {

    private final AuditEventRepository auditEventRepository;

    public RestCallAuditAspect(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        String type = joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName();
        String principal = currentPrincipal();
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            publish(principal, type, "SUCCESS", start, null);
            return result;
        } catch (Throwable t) {
            publish(principal, type, "FAILURE", start, t.getClass().getSimpleName());
            throw t;
        }
    }

    private void publish(String principal, String type, String outcome, long startMillis, String failureReason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("outcome", outcome);
        data.put("durationMs", System.currentTimeMillis() - startMillis);
        if (failureReason != null) {
            data.put("failureReason", failureReason);
        }
        try {
            auditEventRepository.add(new AuditEvent(principal, type, data));
        } catch (Exception e) {
            log.warn("Failed to publish audit event for {}: {}", type, e.toString());
        }
    }

    private String currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "anonymous";
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            return username != null ? username : jwt.getSubject();
        }
        return auth.getName();
    }
}
