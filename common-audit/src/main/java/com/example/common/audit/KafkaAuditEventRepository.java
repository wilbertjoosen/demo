package com.example.common.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Replaces Boot's default in-memory AuditEventRepository: every AuditEvent (both auto-captured REST
 * calls, see RestCallAuditAspect, and Spring Security's own login success/failure events, which Boot
 * auto-publishes through this same interface once it's on the classpath) is published to Kafka
 * instead of held in memory. Querying happens via audit-service's Elasticsearch-backed API, not
 * through this interface, so find() is intentionally a no-op — this bean is write-only by design.
 */
@Component
public class KafkaAuditEventRepository implements AuditEventRepository {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String serviceName;

    public KafkaAuditEventRepository(KafkaTemplate<String, Object> kafkaTemplate,
                                      @Value("${spring.application.name}") String serviceName) {
        this.kafkaTemplate = kafkaTemplate;
        this.serviceName = serviceName;
    }

    @Override
    public void add(AuditEvent event) {
        kafkaTemplate.send(AuditTopics.AUDIT_EVENTS, AuditRecord.from(serviceName, event));
    }

    @Override
    public List<AuditEvent> find(String principal, Instant after, String type) {
        return List.of();
    }
}
