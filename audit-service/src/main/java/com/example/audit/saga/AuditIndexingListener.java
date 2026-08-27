package com.example.audit.saga;
import com.example.audit.model.AuditLogDocument;
import com.example.audit.repository.AuditLogRepository;

import com.example.common.audit.AuditRecord;
import com.example.common.audit.AuditTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditIndexingListener {

    private final AuditLogRepository auditLogRepository;

    @KafkaListener(topics = AuditTopics.AUDIT_EVENTS, groupId = "audit-service")
    public void onAuditRecord(AuditRecord record) {
        auditLogRepository.save(new AuditLogDocument(
                record.service(), record.principal(), record.type(), record.data(), record.timestamp()));
    }
}
