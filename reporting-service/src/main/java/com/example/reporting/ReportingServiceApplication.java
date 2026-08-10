package com.example.reporting;

import com.example.common.audit.AuditSchedulingConfig;
import com.example.common.audit.KafkaAuditEventRepository;
import com.example.common.audit.RestCallAuditAspect;
import com.example.common.security.ApiExceptionHandler;
import com.example.common.security.OpenApiConfig;
import com.example.common.security.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({
        ResourceServerSecurityConfig.class, OpenApiConfig.class, ApiExceptionHandler.class,
        KafkaAuditEventRepository.class, AuditSchedulingConfig.class, RestCallAuditAspect.class})
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
