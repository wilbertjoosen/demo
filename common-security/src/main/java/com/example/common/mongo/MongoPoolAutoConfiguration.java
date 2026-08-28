package com.example.common.mongo;

import com.mongodb.MongoClientSettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * Explicit MongoDB connection-pool sizing, shared across every service instead of duplicated 11
 * times. The MongoDB driver's own defaults ({@code maxSize=100}, no {@code minSize}) are sized for
 * one application talking to one database, not ~11 independent services each opening their own
 * pool against the same MongoDB deployment — left alone, every service's default 100-connection
 * ceiling adds up fast once more than a couple of pods are running per service (see
 * k8s-horizontal-scaling).
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so it applies automatically to any service on the classpath — no per-service wiring needed, same
 * as Spring Boot's own {@code MongoMetricsAutoConfiguration} (which already instruments this pool's
 * metrics whenever a {@code MeterRegistry} bean is present; nothing extra needed here for that).
 * {@code @ConditionalOnMissingBean} lets a service override with its own customizer if it ever
 * needs different sizing.
 */
@AutoConfiguration(before = MongoAutoConfiguration.class)
@ConditionalOnClass(MongoClientSettings.class)
@EnableConfigurationProperties(MongoPoolProperties.class)
public class MongoPoolAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MongoClientSettingsBuilderCustomizer mongoPoolSettingsCustomizer(MongoPoolProperties properties) {
        return builder -> builder.applyToConnectionPoolSettings(pool -> pool
                .minSize(properties.minSize())
                .maxSize(properties.maxSize())
                .maxWaitTime(properties.maxWaitTime().toMillis(), TimeUnit.MILLISECONDS)
                .maxConnectionIdleTime(properties.maxConnectionIdleTime().toMillis(), TimeUnit.MILLISECONDS)
                .maxConnectionLifeTime(properties.maxConnectionLifeTime().toMillis(), TimeUnit.MILLISECONDS));
    }
}
