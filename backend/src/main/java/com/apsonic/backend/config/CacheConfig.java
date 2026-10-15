package com.apsonic.backend.config;

import com.apsonic.backend.modules.cms.dto.CmsConfigResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Redis-backed caching.
 *
 * <ul>
 *   <li>Values are stored as JSON with a fixed target type per cache. The previous default (JDK serialization) failed
 *       with a 500 for every request because the cached DTO was not {@code Serializable}, and open-ended polymorphic
 *       deserialization is avoided on purpose.</li>
 *   <li>Entries expire, so a missed eviction can never leave stale content forever.</li>
 *   <li>A cache failure is logged loudly but never fails the request: public pages must still be served from the
 *       database when Redis misbehaves.</li>
 * </ul>
 * Every new {@code @Cacheable} cache must be registered below with its own typed serializer.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String CMS_CONFIGS = "cmsConfigs";
    static final Duration CMS_CONFIGS_TTL = Duration.ofHours(1);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(10))
                .prefixCacheNameWith("apsonic:");

        RedisCacheConfiguration cmsConfigs = defaults
                .entryTtl(CMS_CONFIGS_TTL)
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(objectMapper, CmsConfigResponse.class)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration(CMS_CONFIGS, cmsConfigs)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /** Logs and swallows cache errors so the underlying method (the database read) still runs. */
    static class LoggingCacheErrorHandler implements CacheErrorHandler {
        @Override
        public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
            log.error("Cache read failed (cache={}, key={}); serving from the source instead", cache.getName(), key, e);
        }

        @Override
        public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
            log.error("Cache write failed (cache={}, key={}); continuing without caching", cache.getName(), key, e);
        }

        @Override
        public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
            // A failed eviction could leave stale content until the entry expires: say so clearly.
            log.error("Cache eviction FAILED (cache={}, key={}); stale data may be served until it expires", cache.getName(), key, e);
        }

        @Override
        public void handleCacheClearError(RuntimeException e, Cache cache) {
            log.error("Cache clear failed (cache={})", cache.getName(), e);
        }
    }
}
