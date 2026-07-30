package com.alns.rcpharm.ghreposscorer.domain.port.in;

import java.util.concurrent.CompletableFuture;

/**
 * Input Port interface for warming the repository cache.
 */
public interface WarmCacheUseCase {
    void warmCache();

    default CompletableFuture<Void> warmCacheAsync() {
        return CompletableFuture.runAsync(this::warmCache);
    }
}
