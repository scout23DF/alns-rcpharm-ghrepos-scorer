package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheKeyGenerator;
import io.quarkus.cache.CacheName;
import io.quarkus.cache.CaffeineCache;
import io.quarkus.cache.redis.runtime.RedisCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class SimpleCacheManagerProxy implements CacheKeyGenerator {

    private static final Logger log = Logger.getLogger(SimpleCacheManagerProxy.class);

    @Inject
    @CacheName("github-repositories")
    private Cache gitHubReposCache;

    private CaffeineCache caffeineCache;
    private RedisCache redisCache;
    private CacheProviderTypeEnum cacheProviderType = CacheProviderTypeEnum.UNKNOWN;

    private synchronized void initialize() {
        if (gitHubReposCache != null) {

            switch (CacheProviderTypeEnum.fromCacheManager(gitHubReposCache)) {
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
        String strKey = generateSimpleKey(keyAttribsArray[0], keyAttribsArray[1]);

        switch (cacheProviderType) {
            case CAFFEINE:
                return this.caffeineCache.getIfPresent(strKey) != null;
            case REDIS:
                Object raw = this.redisCache.getOrNull(strKey).await().indefinitely();
                return (raw != null);
            default:
                return false;
        }

    }

    @SuppressWarnings("unchecked")
    public <TObjResult> TObjResult get(String... keyAttribsArray) {
        initialize();
        String strKey = generateSimpleKey(keyAttribsArray[0], keyAttribsArray[1]);

        switch (cacheProviderType) {
            case CAFFEINE:
                try {
                return (TObjResult) caffeineCache.getIfPresent(strKey).get();
                } catch (Exception e) {
                    log.warn("Error retrieving value from Caffeine cache for key: " + strKey + ". Exception: " + e.getMessage());
                    return null;
                }
            case REDIS:
                return (TObjResult) redisCache.get(strKey, k -> null).await().indefinitely();
            default:
                return null;
        }

    }

    public void put(Object valueToStore, String... keyAttribsArray) {
        initialize();
        String strKey = generateSimpleKey(keyAttribsArray[0], keyAttribsArray[1]);

        switch (cacheProviderType) {
            case CAFFEINE:
                try {
                    caffeineCache.put(strKey, CompletableFuture.completedFuture(valueToStore));
                } catch (Exception e) {
                    log.warn("Error storing value in Caffeine cache for key: " + strKey + ". Exception: " + e.getMessage());
                }
                break;
            case REDIS:
                redisCache.put(strKey, valueToStore).await().indefinitely();
                break;
            default:
                break;
        }

    }

    public <TObjResult> boolean containsValidListOf(String... keyAttribsArray) {

        boolean bolResult = false;

        try {
            boolean keyExistsInCache = this.containsKey(keyAttribsArray);

            if (keyExistsInCache) {
                List<TObjResult> cachedGHRepositoriesList = this.get(keyAttribsArray);

                if (cachedGHRepositoriesList != null && !cachedGHRepositoriesList.isEmpty()) {
                    log.infof("Cache contains valid List of GHRepositories for key: %s - List's Size: %d",
                              (Object) keyAttribsArray,
                              cachedGHRepositoriesList.size());
                    bolResult = true;
                }
            }
        } catch (Exception ex) {
            this.gitHubReposCache.invalidate(generateSimpleKey(keyAttribsArray[0], keyAttribsArray[1]));
            log.warn("Error checking cache Value for Key: " + keyAttribsArray + ". Removing entry from the Cache. Exception: " + ex.getMessage());
            bolResult = false;
        }

        return bolResult;

    }

    @Override
    public Object generate(Method method, Object... methodParams) {
        return generateSimpleKey(String.valueOf(methodParams[0]), String.valueOf(methodParams[1]));
    }

    public String generateSimpleKey(String language, String createdAfter) {
        return language.toLowerCase(Locale.ROOT) + "::" + createdAfter;
    }
}
