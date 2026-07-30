package com.alns.rcpharm.ghreposscorer.springboot.scheduler;

import com.alns.rcpharm.ghreposscorer.domain.port.in.WarmCacheUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheWarmerScheduler {

    private final WarmCacheUseCase warmCacheUseCase;

    public CacheWarmerScheduler(WarmCacheUseCase warmCacheUseCase) {
        this.warmCacheUseCase = warmCacheUseCase;
    }

    @Scheduled(fixedRateString = "${cache.warmer.fixed-rate:3600000}")
    public void warmCache() {
        warmCacheUseCase.warmCacheAsync();
    }
}
