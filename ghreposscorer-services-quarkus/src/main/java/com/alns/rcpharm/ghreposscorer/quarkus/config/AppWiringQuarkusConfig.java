package com.alns.rcpharm.ghreposscorer.quarkus.config;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.ListScoredGHReposRankingStreamUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.ListScoredGHReposRankingUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.WarmCacheUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.out.CacheInvalidatorPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import com.alns.rcpharm.ghreposscorer.domain.service.CacheWarmerService;
import com.alns.rcpharm.ghreposscorer.domain.service.ListScoredGHReposRankingService;
import com.alns.rcpharm.ghreposscorer.domain.service.ListScoredGHReposRankingStreamService;
import com.alns.rcpharm.ghreposscorer.domain.service.UpdateScoreConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class AppWiringQuarkusConfig {

    @Produces
    @ApplicationScoped
    public ScoreConfigStoragePort scoreConfigStoragePort() {
        return new ScoreConfigStoragePort() {
            private final AtomicReference<ScoreConfig> storage = new AtomicReference<>(ScoreConfig.defaultConfig());

            @Override
            public ScoreConfig loadConfig() {
                return storage.get();
            }

            @Override
            public void saveConfig(ScoreConfig config) {
                storage.set(config);
            }
        };
    }

    @Produces
    @ApplicationScoped
    @Typed({ListScoredGHReposRankingUseCase.class, ListScoredGHReposRankingService.class})
    public ListScoredGHReposRankingService popularityCalculatorService(
            GitHubRepositoryPort gitHubRepositoryPort,
            UpdateScoreConfigUseCase updateScoreConfigUseCase) {
        return new ListScoredGHReposRankingService(gitHubRepositoryPort, updateScoreConfigUseCase);
    }

    @Produces
    @ApplicationScoped
    @Typed({ListScoredGHReposRankingStreamUseCase.class, ListScoredGHReposRankingStreamService.class})
    public ListScoredGHReposRankingStreamService popularityCalculatorStreamService(
            GitHubRepositoryPort gitHubRepositoryPort,
            UpdateScoreConfigUseCase updateScoreConfigUseCase) {
        return new ListScoredGHReposRankingStreamService(gitHubRepositoryPort, updateScoreConfigUseCase);
    }

    @Produces
    @ApplicationScoped
    @Typed({UpdateScoreConfigUseCase.class, UpdateScoreConfigService.class})
    public UpdateScoreConfigService updateScoreConfigService(
            ScoreConfigStoragePort scoreConfigStoragePort,
            CacheInvalidatorPort cacheInvalidatorPort,
            WarmCacheUseCase warmCacheUseCase) {
        return new UpdateScoreConfigService(scoreConfigStoragePort, cacheInvalidatorPort, warmCacheUseCase);
    }

    @Produces
    @ApplicationScoped
    @Typed({WarmCacheUseCase.class, CacheWarmerService.class})
    public CacheWarmerService cacheWarmerService(
            ListScoredGHReposRankingUseCase listScoredGHReposRankingUseCase,
            UpdateScoreConfigUseCase updateScoreConfigUseCase,
            ManagedExecutor managedExecutor) {
        return new CacheWarmerService(listScoredGHReposRankingUseCase, updateScoreConfigUseCase, managedExecutor);
    }
}
