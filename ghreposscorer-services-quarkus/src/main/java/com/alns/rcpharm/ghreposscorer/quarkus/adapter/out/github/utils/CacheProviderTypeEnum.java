package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CaffeineCache;
import io.quarkus.cache.redis.runtime.RedisCache;

public enum CacheProviderTypeEnum {

    CAFFEINE,
    REDIS,
    UNKNOWN;

    public static CacheProviderTypeEnum fromCacheManager(Cache cacheGitRepos) {
        if (cacheGitRepos == null) {
            return UNKNOWN;
        }

        if (cacheGitRepos instanceof CaffeineCache) {
            return CAFFEINE;
        }
        if (cacheGitRepos instanceof RedisCache) {
            return REDIS;
        }

        return UNKNOWN;
    }
}
