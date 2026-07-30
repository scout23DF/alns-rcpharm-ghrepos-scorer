package com.alns.rcpharm.ghreposscorer.domain.service;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.CalculatePopularityUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.WarmCacheUseCase;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Domain Service responsible for executing cache warming for popular languages.
 */
public class CacheWarmerService implements WarmCacheUseCase {

    private static final Logger log = Logger.getLogger(CacheWarmerService.class.getName());

    private final CalculatePopularityUseCase calculatePopularityUseCase;
    private final UpdateScoreConfigUseCase updateScoreConfigUseCase;
    private final Executor executor;

    public CacheWarmerService(CalculatePopularityUseCase calculatePopularityUseCase,
                              UpdateScoreConfigUseCase updateScoreConfigUseCase) {
        this(calculatePopularityUseCase, updateScoreConfigUseCase, null);
    }

    public CacheWarmerService(CalculatePopularityUseCase calculatePopularityUseCase,
                              UpdateScoreConfigUseCase updateScoreConfigUseCase,
                              Executor executor) {
        this.calculatePopularityUseCase = calculatePopularityUseCase;
        this.updateScoreConfigUseCase = updateScoreConfigUseCase;
        this.executor = executor;
    }

    @Override
    public void warmCache() {
        log.info("Starting background cache warmer for popular languages...");
        ScoreConfig config = updateScoreConfigUseCase.getCurrentConfig();
        for (String lang : config.popularLanguages()) {
            try {
                calculatePopularityUseCase.getPopularRepositories(
                        lang,
                        config.defaultCreatedAfter(),
                        config.defaultPopularityLimit()
                );
                log.info("Cache warmed for language: " + lang);
                if (config.delayBetweenGHApiRequestsMillis() != null && config.delayBetweenGHApiRequestsMillis() > 0) {
                    Thread.sleep(config.delayBetweenGHApiRequestsMillis());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.severe("Failed to warm cache for language " + lang + ": " + e.getMessage());
            }
        }
        log.info("Cache warmer completed.");
    }

    @Override
    public CompletableFuture<Void> warmCacheAsync() {
        if (executor != null) {
            return CompletableFuture.runAsync(this::warmCache, executor);
        }
        return CompletableFuture.runAsync(this::warmCache);
    }
}
