package com.alns.rcpharm.ghreposscorer.domain.service;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.WarmCacheUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.out.CacheInvalidatorPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;

import java.util.Objects;

/**
 * Domain Service implementing dynamic configuration management.
 */
public class UpdateScoreConfigService implements UpdateScoreConfigUseCase {

    private final ScoreConfigStoragePort scoreConfigStoragePort;
    private final CacheInvalidatorPort cacheInvalidatorPort;
    private final WarmCacheUseCase warmCacheUseCase;

    protected UpdateScoreConfigService() {
        this.scoreConfigStoragePort = null;
        this.cacheInvalidatorPort = null;
        this.warmCacheUseCase = null;
    }

    public UpdateScoreConfigService(ScoreConfigStoragePort scoreConfigStoragePort,
                                    CacheInvalidatorPort cacheInvalidatorPort,
                                    WarmCacheUseCase warmCacheUseCase) {

        this.scoreConfigStoragePort = Objects.requireNonNull(scoreConfigStoragePort, "scoreConfigStoragePort must not be null");
        this.cacheInvalidatorPort = cacheInvalidatorPort;
        this.warmCacheUseCase = warmCacheUseCase;
    }

    @Override
    public ScoreConfig updateConfig(ScoreConfig newConfig) {
        Objects.requireNonNull(newConfig, "newConfig must not be null");
        scoreConfigStoragePort.saveConfig(newConfig);

        if (cacheInvalidatorPort != null) {
            cacheInvalidatorPort.invalidateCache();
        }

        if (warmCacheUseCase != null) {
            warmCacheUseCase.warmCacheAsync();
        }

        return newConfig;
    }

    @Override
    public ScoreConfig getCurrentConfig() {
        ScoreConfig loaded = scoreConfigStoragePort.loadConfig();
        return loaded != null ? loaded : ScoreConfig.defaultConfig();
    }

}
