package com.example.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface AuditLogRepository extends ElasticsearchRepository<AuditLogDocument, String> {

    Page<AuditLogDocument> findByService(String service, Pageable pageable);

    Page<AuditLogDocument> findByPrincipal(String principal, Pageable pageable);

    Page<AuditLogDocument> findByServiceAndPrincipal(String service, String principal, Pageable pageable);
}
