package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.cache;

import com.alns.rcpharm.ghreposscorer.domain.port.out.CacheInvalidatorPort;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Quarkus adapter implementing CacheInvalidatorPort to clear repository score cache.
 */
@ApplicationScoped
public class QuarkusCacheInvalidatorAdapter implements CacheInvalidatorPort {

    @Inject
    @CacheName("github-repositories")
    Cache cache;

    @Override
    public void invalidateCache() {
        if (cache != null) {
            cache.invalidateAll().await().indefinitely();
        }
    }
}
