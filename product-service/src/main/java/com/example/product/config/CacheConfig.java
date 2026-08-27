package com.example.product.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Spring's default RedisCacheManager uses JDK serialization, which requires every cached type to
 * implement Serializable — Product doesn't, and shouldn't just to satisfy the cache. JSON
 * serialization works with any POJO and is human-readable in Redis besides.
 */
@Configuration
@Slf4j
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        GenericJacksonJsonRedisSerializer.builder()
                                // Match the old no-arg GenericJackson2JsonRedisSerializer: write @class type
                                // hints so arbitrary POJOs round-trip, and handle Spring Cache's NullValue
                                // marker (defaultCacheConfig() caches nulls).
                                .enableUnsafeDefaultTyping()
                                .enableSpringCacheNullValueSupport()
                                .build()));
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
