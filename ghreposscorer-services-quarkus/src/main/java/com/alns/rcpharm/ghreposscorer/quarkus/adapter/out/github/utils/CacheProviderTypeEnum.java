package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CaffeineCache;
import io.quarkus.cache.redis.runtime.RedisCache;

import java.util.Optional;

public enum CacheProviderTypeEnum {

    CAFFEINE,
    REDIS,
    UNKNOWN;

    public static CacheProviderTypeEnum fromCacheManager(CacheManager cacheManager) {
        if (cacheManager == null) {
            return UNKNOWN;
        }

        Cache cacheGitRepos = Optional.ofNullable(cacheManager)
                .flatMap(cm -> cm.getCache("github-repositories"))
                .orElse(null);

        if (cacheGitRepos != null) {
            if (cacheGitRepos instanceof CaffeineCache) {
                return CAFFEINE;
            }
            if (cacheGitRepos instanceof RedisCache) {
                return REDIS;
            }
        }

        return UNKNOWN;
    }
}
