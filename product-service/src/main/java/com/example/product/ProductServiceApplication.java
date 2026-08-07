package com.example.product;

import com.example.common.audit.JwtAuditorAware;
import com.example.common.audit.AuditSchedulingConfig;
import com.example.common.audit.KafkaAuditEventRepository;
import com.example.common.audit.MongoAuditingEnabler;
import com.example.common.audit.RestCallAuditAspect;
import com.example.common.security.ApiExceptionHandler;
import com.example.common.security.MessageSourceConfig;
import com.example.common.security.OpenApiConfig;
import com.example.common.security.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableCaching
@Import({ResourceServerSecurityConfig.class, OpenApiConfig.class, MessageSourceConfig.class, ApiExceptionHandler.class,
        KafkaAuditEventRepository.class, AuditSchedulingConfig.class, RestCallAuditAspect.class, JwtAuditorAware.class, MongoAuditingEnabler.class})
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
