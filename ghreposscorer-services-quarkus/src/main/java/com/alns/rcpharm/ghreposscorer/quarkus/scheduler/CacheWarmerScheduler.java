package com.alns.rcpharm.ghreposscorer.quarkus.scheduler;

import com.alns.rcpharm.ghreposscorer.domain.port.in.WarmCacheUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CacheWarmerScheduler {

    @Inject
    WarmCacheUseCase warmCacheUseCase;

    @Scheduled(every = "1h")
    public void warmCache() {
        warmCacheUseCase.warmCacheAsync();
    }
}
