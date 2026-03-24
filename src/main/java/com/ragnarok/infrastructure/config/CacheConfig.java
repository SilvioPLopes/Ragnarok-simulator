package com.ragnarok.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * Named caches with their expiry policies.
     *
     * Static game tables (weapon size modifiers, skill buff effects, skill tree)
     * never change at runtime — no TTL, bounded size.
     *
     * Player-specific data (learned skills) changes when a skill is learned —
     * 5-minute TTL as a safety net, with explicit eviction on aprenderSkill().
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache("weaponSizeModifiers",
                        Caffeine.newBuilder().maximumSize(1000).recordStats()),
                buildCache("skillBuffEffects",
                        Caffeine.newBuilder().maximumSize(1000).recordStats()),
                buildCache("skillTree",
                        Caffeine.newBuilder().maximumSize(100).recordStats()),
                buildCache("playerSkills",
                        Caffeine.newBuilder()
                                .expireAfterWrite(5, TimeUnit.MINUTES)
                                .maximumSize(500)
                                .recordStats())
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, Caffeine<Object, Object> caffeine) {
        return new CaffeineCache(name, caffeine.build());
    }
}
