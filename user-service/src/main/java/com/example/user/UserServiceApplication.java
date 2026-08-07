package com.example.user;

import com.example.common.audit.JwtAuditorAware;
import com.example.common.audit.AuditSchedulingConfig;
import com.example.common.audit.KafkaAuditEventRepository;
import com.example.common.audit.MongoAuditingEnabler;
import com.example.common.audit.RestCallAuditAspect;
import com.example.common.security.ApiExceptionHandler;
import com.example.common.security.OpenApiConfig;
import com.example.common.security.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({ResourceServerSecurityConfig.class, OpenApiConfig.class, ApiExceptionHandler.class, KafkaAuditEventRepository.class, AuditSchedulingConfig.class,
        RestCallAuditAspect.class, JwtAuditorAware.class, MongoAuditingEnabler.class})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
