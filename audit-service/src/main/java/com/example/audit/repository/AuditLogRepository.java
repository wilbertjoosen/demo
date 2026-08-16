package com.example.audit.repository;

import com.example.audit.model.AuditLogDocument;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface AuditLogRepository extends ElasticsearchRepository<AuditLogDocument, String> {

    Page<AuditLogDocument> findByService(String service, Pageable pageable);

    Page<AuditLogDocument> findByPrincipal(String principal, Pageable pageable);

    Page<AuditLogDocument> findByServiceAndPrincipal(String service, String principal, Pageable pageable);

    /** {@code data} is a dynamically-mapped object, so a derived findByData_RecordId method won't reach it — raw term query instead. */
    @Query("{\"term\": {\"data.recordId.keyword\": \"?0\"}}")
    List<AuditLogDocument> findByRecordId(String recordId, Sort sort);
}
