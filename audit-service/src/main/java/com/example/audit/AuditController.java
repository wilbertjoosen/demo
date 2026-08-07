package com.example.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

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
}
