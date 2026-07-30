package com.alns.rcpharm.ghreposscorer.domain.port.out;

/**
 * Output Port interface for invalidating repository score cache.
 */
public interface CacheInvalidatorPort {
    void invalidateCache();
}
