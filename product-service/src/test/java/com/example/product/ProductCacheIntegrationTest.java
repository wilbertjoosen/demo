package com.example.product;

import com.example.product.model.Product;
import com.example.product.service.ProductService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

/**
 * Real Redis + MongoDB via Testcontainers — proves two things unit tests (mocked CacheManager)
 * can't, and caught two real bugs doing it (both fixed in CacheConfig, see its javadoc): entries
 * silently never expiring ({@code spring.cache.redis.time-to-live} was being ignored whenever a
 * custom {@code RedisCacheConfiguration} bean is present), and {@code @CacheEvict(allEntries =
 * true)} racing with an immediately-following read (Spring Data Redis 4.1's default async cache
 * writes with Lettuce).
 */
@SpringBootTest
@Testcontainers
class ProductCacheIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    // create()/update()/delete() all publish to Kafka — without a real broker, kafkaTemplate.send()
    // times out after 60s and every test here fails, not just skips the publish. (Locally this can
    // go unnoticed if an ambient Kafka happens to be running on localhost:9092 from
    // local-infra.sh — CI has no such thing, which is exactly how this was missed initially.)
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void shortTtlForThisTest(DynamicPropertyRegistry registry) {
        // Real config uses 60s (application.yaml) — too slow for a test to wait out. 2s proves the
        // exact same mechanism actually expires entries, without the test taking a minute to run.
        registry.add("spring.cache.redis.time-to-live", () -> "2s");
    }

    @Autowired
    ProductService productService;
    @Autowired
    MongoTemplate mongoTemplate;

    @Test
    void get_cachesResult_soDirectDbMutationIsInvisibleUntilTtlExpires() {
        Product saved = productService.create(new Product("SKU-1", "Widget", new BigDecimal("9.99")));
        Product firstRead = productService.get(saved.getId());
        assertThat(firstRead.getName()).isEqualTo("Widget");

        // Bypasses the service layer (and its @CacheEvict) entirely — the only way this change
        // becomes visible is if the cache actually expires and get() falls through to Mongo again.
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(saved.getId())),
                new Update().set("name", "Widget (mutated directly in Mongo)"), Product.class);

        assertThat(productService.get(saved.getId()).getName())
                .as("still within the 2s TTL — must still be served from cache, not the mutated Mongo document")
                .isEqualTo("Widget");

        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(productService.get(saved.getId()).getName())
                        .as("past the 2s TTL — cache must have expired, falling through to Mongo")
                        .isEqualTo("Widget (mutated directly in Mongo)"));
    }

    @Test
    void update_evictsCache_soSubsequentGetReturnsFreshData() {
        Product saved = productService.create(new Product("SKU-2", "Gadget", new BigDecimal("19.99")));
        productService.get(saved.getId());

        Product patch = new Product("SKU-2", "Gadget v2", new BigDecimal("24.99"));
        productService.update(saved.getId(), patch);

        assertThat(productService.get(saved.getId()).getName())
                .as("update() evicts this id's cache entry — get() must return the fresh name immediately, not wait out the TTL")
                .isEqualTo("Gadget v2");
    }

    @Test
    void list_isEvictedOnCreateUpdateAndDelete() {
        assertThat(productService.list()).extracting(Product::getSku).doesNotContain("SKU-3");

        Product saved = productService.create(new Product("SKU-3", "Thing", new BigDecimal("1.00")));
        assertThat(productService.list())
                .as("create() evicts the list cache — the new product must appear without waiting out the TTL")
                .extracting(Product::getId).contains(saved.getId());

        productService.delete(saved.getId());
        assertThat(productService.list())
                .as("delete() evicts the list cache — the removed product must disappear immediately")
                .extracting(Product::getId).doesNotContain(saved.getId());
    }
}
