package com.example.common.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Needed for {@link KafkaAuditEventRepository}'s periodic queue-drain task. */
@Configuration
@EnableScheduling
public class AuditSchedulingConfig {
}
