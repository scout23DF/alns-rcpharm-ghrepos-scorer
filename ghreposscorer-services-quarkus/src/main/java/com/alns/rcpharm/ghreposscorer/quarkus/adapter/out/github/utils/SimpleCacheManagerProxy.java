package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CaffeineCache;
import io.quarkus.cache.CompositeCacheKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class SimpleCacheManagerProxy {

    private static final Logger log = Logger.getLogger(SimpleCacheManagerProxy.class);

    @Inject
    private CacheManager cacheManager;

    private Cache genericCache;
    private CaffeineCache caffeineCache;
    private CacheProviderTypeEnum cacheProviderType = CacheProviderTypeEnum.UNKNOWN;

    private void initialize() {
        if (genericCache != null) {
            return;
        }
        genericCache = Optional.ofNullable(cacheManager)
                .flatMap(cm -> cm.getCache("github-repositories"))
                .orElse(null);

        if (genericCache != null) {
            this.cacheProviderType = CacheProviderTypeEnum.fromCacheManager(cacheManager);
            if (this.cacheProviderType == CacheProviderTypeEnum.CAFFEINE) {
                try {
                    caffeineCache = genericCache.as(CaffeineCache.class);
                } catch (Exception e) {
                    this.cacheProviderType = CacheProviderTypeEnum.REDIS;
                }
            }
        }
    }

    public boolean containsKey(String... keyAttribsArray) {
        initialize();
        if (genericCache == null) return false;

        CompositeCacheKey key = new CompositeCacheKey(keyAttribsArray);
        if (caffeineCache != null) {
            return caffeineCache.getIfPresent(key) != null;
        }

        try {
            Object result = genericCache.get(key, k -> null).await().indefinitely();
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public <TObjResult> TObjResult get(String... keyAttribsArray) {
        initialize();
        if (genericCache == null) return null;

        CompositeCacheKey key = new CompositeCacheKey(keyAttribsArray);
        if (caffeineCache != null) {
            try {
                CompletableFuture<Object> future = caffeineCache.getIfPresent(key);
                return future != null ? (TObjResult) future.get() : null;
            } catch (Exception e) {
                log.warn("Error retrieving value from Caffeine cache for key: " + key + ". Exception: " + e.getMessage());
                return null;
            }
        }

        try {
            return (TObjResult) genericCache.get(key, k -> null).await().indefinitely();
        } catch (Exception e) {
            log.warn("Error retrieving value from Redis/Generic cache for key: " + key + ". Exception: " + e.getMessage());
            return null;
        }
    }

    public void put(Object valueToStore, String... keyAttribsArray) {
        initialize();
        if (genericCache == null || valueToStore == null) return;

        CompositeCacheKey key = new CompositeCacheKey(keyAttribsArray);
        if (caffeineCache != null) {
            caffeineCache.put(key, CompletableFuture.completedFuture(valueToStore));
            return;
        }

        try {
            genericCache.get(key, k -> valueToStore).await().indefinitely();
        } catch (Exception e) {
            log.warn("Error storing value into Redis/Generic cache for key: " + key + ". Exception: " + e.getMessage());
        }
    }
}
