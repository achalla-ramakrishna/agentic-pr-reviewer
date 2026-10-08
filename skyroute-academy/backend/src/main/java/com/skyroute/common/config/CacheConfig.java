package com.skyroute.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two caches with very different freshness needs:
 * <ul>
 *   <li>{@code airports}: reference data, changes rarely -> long TTL.</li>
 *   <li>{@code flightSearch}: seat counts change on every booking -> short TTL
 *       plus explicit eviction when a flight is updated.</li>
 * </ul>
 * In a multi-instance deployment you'd move these to Redis/Hazelcast so evictions
 * are seen by every node; the {@code @Cacheable} code doesn't change.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String AIRPORTS = "airports";
    public static final String FLIGHT_SEARCH = "flightSearch";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(AIRPORTS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(12)).maximumSize(5_000).recordStats().build());
        manager.registerCustomCache(FLIGHT_SEARCH, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(10_000).recordStats().build());
        return manager;
    }
}
