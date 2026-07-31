package com.alns.rcpharm.ghreposscorer.springboot.config;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
public class AppWiringSpringConfig {

    @Bean
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

    @Bean
    public UpdateScoreConfigUseCase updateScoreConfigUseCase(
            ScoreConfigStoragePort scoreConfigStoragePort,
            CacheInvalidatorPort cacheInvalidatorPort,
            @Lazy WarmCacheUseCase warmCacheUseCase) {
        return new UpdateScoreConfigService(scoreConfigStoragePort, cacheInvalidatorPort, warmCacheUseCase);
    }

    @Bean
    public ListScoredGHReposRankingUseCase listScoredGHReposRankingUseCase(
            GitHubRepositoryPort gitHubRepositoryPort,
            UpdateScoreConfigUseCase updateScoreConfigUseCase) {
        return new ListScoredGHReposRankingService(gitHubRepositoryPort, updateScoreConfigUseCase);
    }

    @Bean
    public ListScoredGHReposRankingStreamUseCase listScoredGHReposRankingStreamUseCase(
            GitHubRepositoryPort gitHubRepositoryPort,
            UpdateScoreConfigUseCase updateScoreConfigUseCase) {
        return new ListScoredGHReposRankingStreamService(gitHubRepositoryPort, updateScoreConfigUseCase);
    }

    @Bean
    public CacheWarmerService cacheWarmerService(
            ListScoredGHReposRankingUseCase listScoredGHReposRankingUseCase,
            UpdateScoreConfigUseCase updateScoreConfigUseCase,
            @Qualifier("applicationTaskExecutor") Executor taskExecutor) {
        return new CacheWarmerService(listScoredGHReposRankingUseCase, updateScoreConfigUseCase, taskExecutor);
    }
}
