package com.example.audit.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.Map;

@Document(indexName = "audit-log", createIndex = false)
@Getter
@Setter
@NoArgsConstructor
public class AuditLogDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String service;

    @Field(type = FieldType.Keyword)
    private String principal;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Object)
    private Map<String, Object> data;

    @Field(type = FieldType.Date)
    private Instant timestamp;

    public AuditLogDocument(String service, String principal, String type, Map<String, Object> data, Instant timestamp) {
        this.service = service;
        this.principal = principal;
        this.type = type;
        this.data = data;
        this.timestamp = timestamp;
    }
}
