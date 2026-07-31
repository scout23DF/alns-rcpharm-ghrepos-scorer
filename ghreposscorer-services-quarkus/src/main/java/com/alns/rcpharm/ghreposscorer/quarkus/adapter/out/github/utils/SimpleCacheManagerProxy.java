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

    private CaffeineCache caffeineCache;
    private CacheProviderTypeEnum cacheProviderType = CacheProviderTypeEnum.UNKNOWN;

    private void initialize() {
        Cache cacheGitRepos = Optional.ofNullable(cacheManager)
                .flatMap(cm -> cm.getCache("github-repositories"))
                .orElse(null);

        if (cacheGitRepos != null) {

            this.cacheProviderType = CacheProviderTypeEnum.fromCacheManager(cacheManager);

            switch (cacheProviderType) {
                case REDIS:
                    // Initialize Redis key if needed
                    break;
                case UNKNOWN:
                    // Handle unknown cache provider if needed
                    break;
                default: // CAFFEINE:
                    caffeineCache = cacheGitRepos.as(io.quarkus.cache.CaffeineCache.class);
            }

        }
    }

    public boolean containsKey(String... keyAttribsArray) {
        initialize();
        switch (cacheProviderType) {
            case CAFFEINE:
                CompositeCacheKey caffeineKey = new CompositeCacheKey(keyAttribsArray);
                return caffeineCache.getIfPresent(caffeineKey) != null;
            case REDIS:
                // Initialize Redis key if needed
                return false;
            default:
                // Handle unknown cache provider if needed
                return false;
        }

    }

    public <TObjResult> TObjResult get(String... keyAttribsArray) {
        initialize();
        switch (cacheProviderType) {
            case CAFFEINE:
                CompositeCacheKey caffeineKey = new CompositeCacheKey(keyAttribsArray);
                try {
                    return (TObjResult) caffeineCache.getIfPresent(caffeineKey).get();
                } catch (Exception e) {
                    log.warn("Error retrieving value from cache for key: " + caffeineKey + ". Exception: " + e.getMessage());
                    return null;
                }
            case REDIS:
                // Initialize Redis key if needed
                return null;
            default:
                // Handle unknown cache provider if needed
                return null;
        }

    }

    public void put(Object valueToStore, String... keyAttribsArray) {
        initialize();
        switch (cacheProviderType) {
            case CAFFEINE:
                CompositeCacheKey caffeineKey = new CompositeCacheKey(keyAttribsArray);
                caffeineCache.put(caffeineKey, CompletableFuture.completedFuture(valueToStore));
                break;
            case REDIS:
                // Initialize Redis key if needed
                break;
            default:
                // Handle unknown cache provider if needed
                break;
        }

    }

}
