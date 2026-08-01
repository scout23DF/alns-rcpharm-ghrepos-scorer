package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CaffeineCache;
import io.quarkus.cache.CompositeCacheKey;
import io.quarkus.cache.redis.runtime.RedisCache;
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

    private Cache gitHubReposCache;
    private CaffeineCache caffeineCache;
    private RedisCache redisCache;
    private CacheProviderTypeEnum cacheProviderType = CacheProviderTypeEnum.UNKNOWN;

    private synchronized void initialize() {
        if (gitHubReposCache != null) {
            return;
        }
        gitHubReposCache = Optional.ofNullable(cacheManager)
                .flatMap(cm -> cm.getCache("github-repositories"))
                .orElse(null);

        if (gitHubReposCache != null) {

            switch (CacheProviderTypeEnum.fromCacheManager(cacheManager)) {
                case CAFFEINE:
                    this.caffeineCache = (CaffeineCache) gitHubReposCache;
                    this.cacheProviderType = CacheProviderTypeEnum.CAFFEINE;
                    break;
                case REDIS:
                    this.redisCache = (RedisCache) gitHubReposCache;
                    this.cacheProviderType = CacheProviderTypeEnum.REDIS;
                    break;
                default:
                    this.cacheProviderType = CacheProviderTypeEnum.UNKNOWN;
                    break;
            }
        }
    }

    public boolean containsKey(String... keyAttribsArray) {
        initialize();

        switch (cacheProviderType) {
            case CAFFEINE:
                CompositeCacheKey key = new CompositeCacheKey(keyAttribsArray);
                return this.caffeineCache.getIfPresent(key) != null;
            case REDIS:
                String strKey = keyAttribsArray[0] + "-" + keyAttribsArray[1];
                return this.redisCache.getOrNull(strKey) != null;
            default:
                return false;
        }

    }

    @SuppressWarnings("unchecked")
    public <TObjResult> TObjResult get(String... keyAttribsArray) {
        initialize();

        switch (cacheProviderType) {
            case CAFFEINE:
                CompositeCacheKey key = new CompositeCacheKey(keyAttribsArray);
                try {
                return (TObjResult) caffeineCache.getIfPresent(key).get();
                } catch (Exception e) {
                    log.warn("Error retrieving value from Caffeine cache for key: " + key + ". Exception: " + e.getMessage());
                    return null;
                }
            case REDIS:
                String strKey = keyAttribsArray[0] + "-" + keyAttribsArray[1];
                return (TObjResult) redisCache.get(strKey, k -> null).await().indefinitely();
            default:
                return null;
        }

    }

    public void put(Object valueToStore, String... keyAttribsArray) {
        initialize();

        switch (cacheProviderType) {
            case CAFFEINE:
                CompositeCacheKey key = new CompositeCacheKey(keyAttribsArray);
                try {
                    caffeineCache.put(key, CompletableFuture.completedFuture(valueToStore));
                } catch (Exception e) {
                    log.warn("Error storing value in Caffeine cache for key: " + key + ". Exception: " + e.getMessage());
                }
                break;
            case REDIS:
                String strKey = keyAttribsArray[0] + "-" + keyAttribsArray[1];
                redisCache.put(strKey, valueToStore).await().indefinitely();
                break;
            default:
                break;
        }

    }
}
