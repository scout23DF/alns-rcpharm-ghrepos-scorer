package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.utils.AppCacheMgmtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class SimpleCacheManagerProxy implements CacheKeyGenerator {

    private static final Logger log = Logger.getLogger(SimpleCacheManagerProxy.class);

    @Inject
    @CacheName("github-repositories")
    private Cache gitHubReposCache;

    @Inject
    ObjectMapper objectMapper;

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
        String strKey = AppCacheMgmtUtils.generateSimpleCacheKey(keyAttribsArray[0], keyAttribsArray[1]);

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
        String strKey = AppCacheMgmtUtils.generateSimpleCacheKey(keyAttribsArray[0], keyAttribsArray[1]);

        Object rawValue = null;
        switch (cacheProviderType) {
            case CAFFEINE:
                try {
                    rawValue = caffeineCache.getIfPresent(strKey).get();
                } catch (Exception e) {
                    log.warn("Error retrieving value from Caffeine cache for key: " + strKey + ". Exception: " + e.getMessage());
                    return null;
                }
                break;
            case REDIS:
                rawValue = redisCache.get(strKey, k -> null).await().indefinitely();
                break;
            default:
                return null;
        }

        if (rawValue instanceof List<?> rawList) {
            List<GitHubRepository> convertedList = rawList.stream()
                    .map(item -> item instanceof GitHubRepository gh ? gh : objectMapper.convertValue(item, GitHubRepository.class))
                    .toList();
            return (TObjResult) convertedList;
        }

        return (TObjResult) rawValue;
    }

    public void put(Object valueToStore, String... keyAttribsArray) {
        initialize();
        String strKey = AppCacheMgmtUtils.generateSimpleCacheKey(keyAttribsArray[0], keyAttribsArray[1]);

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
            this.gitHubReposCache.invalidate(AppCacheMgmtUtils.generateSimpleCacheKey(keyAttribsArray[0], keyAttribsArray[1]));
            log.warn("Error checking cache Value for Key: " + keyAttribsArray + ". Removing entry from the Cache. Exception: " + ex.getMessage());
            bolResult = false;
        }

        return bolResult;

    }

    @Override
    public Object generate(Method method, Object... methodParams) {
        return AppCacheMgmtUtils.generateSimpleCacheKey(String.valueOf(methodParams[0]), String.valueOf(methodParams[1]));
    }

}
