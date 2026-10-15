package com.apsonic.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThatCode;

class CacheErrorHandlerTest {

    private final CacheConfig.LoggingCacheErrorHandler handler = new CacheConfig.LoggingCacheErrorHandler();
    private final Cache cache = new ConcurrentMapCache("test");
    private final RuntimeException failure = new IllegalStateException("redis is down");

    @Test
    void cacheFailuresNeverBecomeRequestFailures() {
        assertThatCode(() -> handler.handleCacheGetError(failure, cache, "k")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCachePutError(failure, cache, "k", "v")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheEvictError(failure, cache, "k")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheClearError(failure, cache)).doesNotThrowAnyException();
    }
}
