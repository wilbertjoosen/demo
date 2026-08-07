package com.example.common.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** See {@link JpaAuditingEnabler} for why {@code auditorAwareRef} is a fully-qualified class name. */
@Configuration
@EnableMongoAuditing(auditorAwareRef = "com.example.common.audit.JwtAuditorAware")
public class MongoAuditingEnabler {
}
