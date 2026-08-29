package com.example.common.mongo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * MongoDB connection-pool sizing, shared by every service via {@link MongoPoolAutoConfiguration}.
 * Immutable on purpose — pool settings are read once at {@code MongoClient} construction time, so
 * there's no reason for this to be mutable state. Override per environment with env vars, e.g.
 * {@code MONGODB_POOL_MAX_SIZE=100}.
 */
@ConfigurationProperties("mongodb.pool")
public record MongoPoolProperties(
        @DefaultValue("5") int minSize,
        @DefaultValue("50") int maxSize,
        @DefaultValue("10s") Duration maxWaitTime,
        @DefaultValue("5m") Duration maxConnectionIdleTime,
        @DefaultValue("30m") Duration maxConnectionLifeTime) {
}
