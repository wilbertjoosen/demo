package com.example.audit;

import com.example.common.audit.KafkaAuditEventRepository;
import com.example.common.audit.RestCallAuditAspect;
import com.example.common.security.OpenApiConfig;
import com.example.common.security.ResourceServerSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({ResourceServerSecurityConfig.class, OpenApiConfig.class, KafkaAuditEventRepository.class, RestCallAuditAspect.class})
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
