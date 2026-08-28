package com.example.common.mongo;

import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MongoPoolAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MongoPoolAutoConfiguration.class));

    @Test
    void defaultProperties_applyExpectedPoolSettings() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MongoClientSettingsBuilderCustomizer.class);
            ConnectionPoolSettings pool = applyCustomizer(context.getBean(MongoClientSettingsBuilderCustomizer.class));
            assertThat(pool.getMinSize()).isEqualTo(5);
            assertThat(pool.getMaxSize()).isEqualTo(50);
            assertThat(pool.getMaxWaitTime(TimeUnit.SECONDS)).isEqualTo(10);
            assertThat(pool.getMaxConnectionIdleTime(TimeUnit.MINUTES)).isEqualTo(5);
            assertThat(pool.getMaxConnectionLifeTime(TimeUnit.MINUTES)).isEqualTo(30);
        });
    }

    @Test
    void customProperties_overrideDefaults() {
        contextRunner.withPropertyValues("mongodb.pool.max-size=200", "mongodb.pool.min-size=20")
                .run(context -> {
                    ConnectionPoolSettings pool = applyCustomizer(context.getBean(MongoClientSettingsBuilderCustomizer.class));
                    assertThat(pool.getMaxSize()).isEqualTo(200);
                    assertThat(pool.getMinSize()).isEqualTo(20);
                });
    }

    @Test
    void serviceOwnCustomizerBean_takesPrecedenceOverDefault() {
        contextRunner.withBean(MongoClientSettingsBuilderCustomizer.class, () -> b -> { })
                .run(context -> assertThat(context).hasSingleBean(MongoClientSettingsBuilderCustomizer.class));
    }

    private static ConnectionPoolSettings applyCustomizer(MongoClientSettingsBuilderCustomizer customizer) {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();
        customizer.customize(builder);
        return builder.build().getConnectionPoolSettings();
    }
}
