package com.example.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Cross-cutting activity log: every @RestController method call becomes an AuditEvent, with no
 * per-endpoint instrumentation needed in any service. Pointcut matches any public method on a
 * class annotated @RestController — i.e. every REST endpoint, automatically.
 *
 * <p>Also captures a best-effort "what changed": any {@code @PathVariable} value as {@code recordId}
 * (so the trail can be filtered to one entity), and for writes (methods with an {@code @RequestBody}
 * argument) the submitted payload and the resulting response body. This is NOT a true before/after
 * diff — the aspect has no way to know an entity's prior state without querying it, which would
 * require per-service instrumentation. It's what submitted vs what came back, which is enough to see
 * "what changed" for the common create/update case without touching business logic anywhere.
 */
@Aspect
@Component
@Slf4j
public class RestCallAuditAspect {

    private static final Set<String> REDACTED_KEYS = Set.of("password", "secret", "clientsecret", "token", "accesstoken");
    private static final int MAX_BODY_LENGTH = 4000;

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public RestCallAuditAspect(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        String type = joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName();
        String principal = currentPrincipal();
        String recordId = extractRecordId(joinPoint);
        String requestBody = extractRequestBody(joinPoint);
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            Object body = unwrap(result);
            String responseBody = requestBody != null ? serialize(body) : null;
            // Creates have no {id} path variable to key off of at request time — the id only exists
            // in the response, so recordId falls back to that for exactly this case (an update/delete
            // always already has a real path-variable recordId and keeps it).
            String effectiveRecordId = recordId.isEmpty() ? extractIdFromResponse(body) : recordId;
            publish(principal, type, "SUCCESS", start, null, effectiveRecordId, requestBody, responseBody);
            return result;
        } catch (Throwable t) {
            publish(principal, type, "FAILURE", start, t.getClass().getSimpleName(), recordId, requestBody, null);
            throw t;
        }
    }

    private void publish(String principal, String type, String outcome, long startMillis, String failureReason,
                          String recordId, String requestBody, String responseBody) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("outcome", outcome);
        data.put("durationMs", System.currentTimeMillis() - startMillis);
        if (failureReason != null) {
            data.put("failureReason", failureReason);
        }
        if (recordId != null) {
            data.put("recordId", recordId);
        }
        if (requestBody != null) {
            data.put("requestBody", requestBody);
        }
        if (responseBody != null) {
            data.put("responseBody", responseBody);
        }
        try {
            auditEventRepository.add(new AuditEvent(principal, type, data));
        } catch (Exception e) {
            log.warn("Failed to publish audit event for {}: {}", type, e.toString());
        }
    }

    private String extractRecordId(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();
        return IntStream.range(0, parameters.length)
                .filter(i -> parameters[i].isAnnotationPresent(PathVariable.class))
                .mapToObj(i -> args[i])
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private String extractRequestBody(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(RequestBody.class) && args[i] != null) {
                return serialize(args[i]);
            }
        }
        return null;
    }

    private Object unwrap(Object result) {
        return result instanceof ResponseEntity<?> entity ? entity.getBody() : result;
    }

    private String extractIdFromResponse(Object body) {
        if (body == null) {
            return null;
        }
        JsonNode idNode = objectMapper.valueToTree(body).get("id");
        return idNode == null || idNode.isNull() ? null : idNode.asText();
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.valueToTree(value);
            redact(tree);
            String json = objectMapper.writeValueAsString(tree);
            return json.length() > MAX_BODY_LENGTH ? json.substring(0, MAX_BODY_LENGTH) + "...(truncated)" : json;
        } catch (Exception e) {
            return null;
        }
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(name -> {
                if (REDACTED_KEYS.contains(name.toLowerCase())) {
                    objectNode.put(name, "***REDACTED***");
                }
            });
            objectNode.elements().forEachRemaining(this::redact);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::redact);
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
