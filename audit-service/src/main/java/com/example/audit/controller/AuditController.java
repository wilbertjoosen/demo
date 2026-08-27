package com.example.audit.controller;
import com.example.audit.model.AuditLogDocument;
import com.example.audit.model.RecordHistoryEntry;
import com.example.audit.repository.AuditLogRepository;
import com.example.audit.service.RecordHistoryService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final RecordHistoryService recordHistoryService;

    @GetMapping
    public Page<AuditLogDocument> search(@RequestParam(required = false) String service,
                                          @RequestParam(required = false) String principal,
                                          Pageable pageable) {
        if (service != null && principal != null) {
            return auditLogRepository.findByServiceAndPrincipal(service, principal, pageable);
        }
        if (service != null) {
            return auditLogRepository.findByService(service, pageable);
        }
        if (principal != null) {
            return auditLogRepository.findByPrincipal(principal, pageable);
        }
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Full chronological trace for one entity: who touched it, when, and (for successful writes)
     * a field-level diff against its previous state. See RecordHistoryService for how the diff is
     * derived without any per-service before/after instrumentation.
     */
    @GetMapping("/records/{recordId}/history")
    public List<RecordHistoryEntry> recordHistory(@PathVariable String recordId) {
        return recordHistoryService.historyFor(recordId);
    }
}
