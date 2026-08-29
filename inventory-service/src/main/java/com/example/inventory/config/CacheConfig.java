package com.example.inventory.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.cache.autoconfigure.CacheProperties;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Spring's default RedisCacheManager uses JDK serialization, which requires every cached type to
 * implement Serializable. JSON serialization works with any POJO and is human-readable in Redis besides.
 *
 * <p>Supplying this bean at all opts out of Spring Boot's own {@code spring.cache.redis.time-to-live}
 * handling — see product-service's CacheConfig for the full explanation (confirmed against Spring
 * Boot 4.1's actual source) and its ProductCacheIntegrationTest, which caught entries never expiring
 * before this fix.
 */
@Configuration
@Slf4j
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheConfiguration cacheConfiguration(CacheProperties cacheProperties) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        GenericJacksonJsonRedisSerializer.builder()
                                // Match the old no-arg GenericJackson2JsonRedisSerializer: write @class type
                                // hints so arbitrary POJOs round-trip, and handle Spring Cache's NullValue
                                // marker (defaultCacheConfig() caches nulls).
                                .enableUnsafeDefaultTyping()
                                .enableSpringCacheNullValueSupport()
                                .build()));
        if (cacheProperties.getRedis().getTimeToLive() != null) {
            config = config.entryTtl(cacheProperties.getRedis().getTimeToLive());
        }
        return config;
    }

    /**
     * {@code @CacheEvict(allEntries = true)}'s {@code clear()} runs asynchronously by default in
     * Spring Data Redis 4.1 with Lettuce — see product-service's CacheConfig for the full
     * explanation (same root cause, confirmed there against a real Redis via Testcontainers).
     * {@code delete()}'s {@code allEntries = true} evict is the one method here that hits this.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer immediateWritesCustomizer(RedisConnectionFactory connectionFactory) {
        return builder -> builder.cacheWriter(
                RedisCacheWriter.create(connectionFactory, RedisCacheWriter.RedisCacheWriterConfigurer::immediateWrites));
    }

    /**
     * A Redis outage should degrade caching, not take down every @Cacheable endpoint with it —
     * see product-service's CacheConfig for the full rationale. Must go through
     * {@link CachingConfigurer#errorHandler()}, not a standalone {@code @Bean CacheErrorHandler}.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis unavailable for cache '{}' get(key={}) — falling through to the source: {}",
                        cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis unavailable for cache '{}' put(key={}) — skipping cache write: {}",
                        cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis unavailable for cache '{}' evict(key={}) — skipping cache eviction: {}",
                        cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis unavailable for cache '{}' clear() — skipping: {}", cache.getName(), exception.toString());
            }
        };
    }
}
