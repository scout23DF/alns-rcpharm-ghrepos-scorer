package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.cache;

import com.alns.rcpharm.ghreposscorer.domain.port.out.CacheInvalidatorPort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Spring Boot adapter implementing CacheInvalidatorPort to clear repository score cache.
 */
@Component
public class SpringCacheInvalidatorAdapter implements CacheInvalidatorPort {

    private final CacheManager cacheManager;

    public SpringCacheInvalidatorAdapter(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void invalidateCache() {
        Cache cache = cacheManager.getCache("github-repositories");
        if (cache != null) {
            cache.clear();
        }
    }
}
