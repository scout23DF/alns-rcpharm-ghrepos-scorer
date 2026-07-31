package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CaffeineCache;

import java.util.Optional;

public enum CacheProviderTypeEnum {

    CAFFEINE(CaffeineCache.class),
    REDIS(Void.class),
    UNKNOWN(Void.class);

    private Class<?> cacheImplClazz;

    CacheProviderTypeEnum(Class<?> cacheImplClazz) {
        this.cacheImplClazz = cacheImplClazz;
    }

    public static CacheProviderTypeEnum fromCacheManager(CacheManager cacheManager) {
        if (cacheManager == null) {
            return UNKNOWN;
        }

        Cache cacheGitRepos = Optional.ofNullable(cacheManager)
                .flatMap(cm -> cm.getCache("github-repositories"))
                .orElse(null);

        if (cacheGitRepos != null) {
            CaffeineCache caffeineCache = cacheGitRepos.as(CaffeineCache.class);
            if (caffeineCache != null) {
                return CAFFEINE;
            }
        }

        // Add logic for Redis detection if needed

        return UNKNOWN;
    }
}
