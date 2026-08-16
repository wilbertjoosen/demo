package com.example.order.support;
import com.example.order.saga.OrderSagaListener;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real MySQL + MongoDB + Kafka via Testcontainers, one instance shared across the whole test class
 * (no @DirtiesContext / no per-test container) — matches how these services actually run, catching
 * issues mocks can't (see the Jackson-Instant-as-epoch-seconds bug this same project hit earlier).
 * {@code @ServiceConnection} wires each container's connection details into Spring Boot's
 * auto-configured DataSource/MongoClient/KafkaTemplate automatically, no manual property overrides.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(TestOAuth2ClientConfig.class)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    private static final AtomicInteger SAGA_GROUP_SUFFIX = new AtomicInteger();

    /**
     * Every concrete test class's Spring context includes the real OrderSagaListener bean (it's part
     * of the app), so without this, two integration test classes running in the same JVM both join a
     * "order-service" Kafka consumer group — the second one has to wait out the first (now-closing)
     * context's session timeout before a rebalance hands it the partition, which manifested as saga
     * events intermittently taking 20-30+ seconds to arrive. A fresh group id per test class (see
     * OrderSagaListener's groupId property placeholder) sidesteps the collision entirely.
     */
    @DynamicPropertySource
    static void isolatedSagaConsumerGroup(DynamicPropertyRegistry registry) {
        registry.add("order-service.saga-consumer-group", () -> "order-service-test-" + SAGA_GROUP_SUFFIX.incrementAndGet());
    }
}
