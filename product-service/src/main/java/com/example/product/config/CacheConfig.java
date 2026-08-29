package com.example.product.config;

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
 * implement Serializable — Product doesn't, and shouldn't just to satisfy the cache. JSON
 * serialization works with any POJO and is human-readable in Redis besides.
 *
 * <p>Supplying this bean at all opts out of Spring Boot's own {@code spring.cache.redis.time-to-live}
 * handling: {@code RedisCacheConfiguration}'s autoconfiguration only reads that property when no
 * custom {@code RedisCacheConfiguration} bean exists (confirmed against Spring Boot 4.1's actual
 * source — {@code determineConfiguration()} calls {@code getIfAvailable()}, which short-circuits its
 * own TTL-reading {@code createConfiguration()} the moment a bean like this one is present). Reading
 * {@link CacheProperties} and calling {@code entryTtl()} ourselves is what makes application.yaml's
 * {@code time-to-live} actually take effect — see ProductCacheIntegrationTest, which caught this
 * silently not happening before this fix.
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
     * Spring Data Redis 4.1's {@code DefaultRedisCacheWriter} writes {@code @CacheEvict(allEntries
     * = true)}'s {@code clear()} <b>asynchronously</b> by default whenever the connection factory
     * also implements {@code ReactiveRedisConnectionFactory} — true for Lettuce, Spring Boot's
     * default client (confirmed against Spring Data Redis 4.1's actual source: the 3-arg
     * {@code DefaultRedisCacheWriter} constructor hardcodes {@code asynchronousWrites = true} in
     * that case). That means {@code create()}/{@code delete()} here can return to the caller
     * *before* the "productsList" cache is actually cleared in Redis — a real race a request
     * immediately re-listing products right after a write can lose, confirmed by
     * ProductCacheIntegrationTest before this fix (a manual, synchronous
     * {@code cacheManager.getCache("productsList").clear()} call in that test didn't remove the
     * key either — same root cause). {@code immediateWrites()} forces the writer down
     * {@code RedisCacheWriter}'s synchronous path instead.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer immediateWritesCustomizer(RedisConnectionFactory connectionFactory) {
        return builder -> builder.cacheWriter(
                RedisCacheWriter.create(connectionFactory, RedisCacheWriter.RedisCacheWriterConfigurer::immediateWrites));
    }

    /**
     * Without this, Spring's default behavior lets a Redis outage propagate straight up as a
     * 500 on every @Cacheable/@CacheEvict endpoint — caching is meant to speed up MongoDB reads,
     * not become a hard dependency for them. Log and fall through to the real method/repository
     * call instead, same "degrade, don't fail" principle as the rest of this demo's resilience work.
     * Must go through {@link CachingConfigurer#errorHandler()} — a standalone {@code @Bean
     * CacheErrorHandler} is NOT auto-detected by Spring's caching infrastructure.
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
