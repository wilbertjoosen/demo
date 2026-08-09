package com.example.common.audit;

import org.springframework.boot.actuate.audit.AuditEvent;

import java.time.Instant;
import java.util.Map;

/**
 * Wire format for the audit-events Kafka topic. Reuses Spring Boot Actuator's AuditEvent as the
 * in-process model (see KafkaAuditEventRepository), but converts to this plain record for the
 * topic itself — AuditEvent has no default constructor/setters, which is friction for Jackson on
 * the consumer side (audit-service); this record avoids that entirely.
 */
public record AuditRecord(String service, String principal, String type, Map<String, Object> data, Instant timestamp) {

    public AuditRecord {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static AuditRecord from(String service, AuditEvent event) {
        return new AuditRecord(service, event.getPrincipal(), event.getType(), event.getData(), event.getTimestamp());
    }
}
