package com.example.product;

import com.example.product.repository.ProductRepository;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import org.bson.BsonDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@code @ReadPreference("secondaryPreferred")} (ProductRepository.findByDeletedFalse)
 * actually reaches the wire protocol, not just that the annotation compiles — a
 * {@link CommandListener} captures the real {@code find} command MongoDB's driver sends and
 * inspects its {@code $readPreference} field directly. MongoDBContainer always initializes as a
 * (single-node) replica set, which is enough for the driver to tag the command correctly even
 * without a real secondary present to route to.
 */
@SpringBootTest
@Testcontainers
class ProductReadPreferenceIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @TestConfiguration
    static class CommandCaptureConfig {

        static final List<CommandStartedEvent> CAPTURED_FINDS = new CopyOnWriteArrayList<>();

        @Bean
        MongoClientSettingsBuilderCustomizer commandCaptureCustomizer() {
            return builder -> builder.addCommandListener(new CommandListener() {
                @Override
                public void commandStarted(CommandStartedEvent event) {
                    if ("find".equals(event.getCommandName())) {
                        CAPTURED_FINDS.add(event);
                    }
                }
            });
        }
    }

    @Autowired
    ProductRepository productRepository;

    @BeforeEach
    void clearCapturedCommands() {
        CommandCaptureConfig.CAPTURED_FINDS.clear();
    }

    @Test
    void findByDeletedFalse_readsFromSecondaryPreferred() {
        productRepository.findByDeletedFalse();

        assertThat(CommandCaptureConfig.CAPTURED_FINDS).hasSize(1);
        BsonDocument command = CommandCaptureConfig.CAPTURED_FINDS.get(0).getCommand();
        assertThat(command.containsKey("$readPreference"))
                .as("find command must carry a non-primary $readPreference for this to have any effect")
                .isTrue();
        assertThat(command.getDocument("$readPreference").getString("mode").getValue())
                .isEqualTo("secondaryPreferred");
    }

    @Test
    void findByIdAndDeletedFalse_staysOnDefaultReadPreference() {
        productRepository.findByIdAndDeletedFalse("does-not-exist");

        assertThat(CommandCaptureConfig.CAPTURED_FINDS).hasSize(1);
        BsonDocument command = CommandCaptureConfig.CAPTURED_FINDS.get(0).getCommand();
        assertThat(command.containsKey("$readPreference"))
                .as("no @ReadPreference on this method — must use the driver's default (primary), unlike findByDeletedFalse")
                .isFalse();
    }
}
