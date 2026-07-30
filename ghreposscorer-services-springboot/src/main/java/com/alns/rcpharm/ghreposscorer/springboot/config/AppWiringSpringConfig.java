package com.alns.rcpharm.ghreposscorer.springboot.config;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.CalculatePopularityStreamUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.CalculatePopularityUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.out.CacheInvalidatorPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import com.alns.rcpharm.ghreposscorer.domain.service.CacheWarmerService;
import com.alns.rcpharm.ghreposscorer.domain.service.PopularityCalculatorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public PopularityCalculatorService popularityCalculatorService(
            GitHubRepositoryPort gitHubRepositoryPort,
            ScoreConfigStoragePort scoreConfigStoragePort,
            CacheInvalidatorPort cacheInvalidatorPort,
            @Qualifier("applicationTaskExecutor") Executor taskExecutor) {
        return new PopularityCalculatorService(gitHubRepositoryPort, scoreConfigStoragePort, cacheInvalidatorPort, taskExecutor);
    }

    @Bean
    public CacheWarmerService cacheWarmerService(
            CalculatePopularityUseCase calculatePopularityUseCase,
            UpdateScoreConfigUseCase updateScoreConfigUseCase,
            @Qualifier("applicationTaskExecutor") Executor taskExecutor) {
        return new CacheWarmerService(calculatePopularityUseCase, updateScoreConfigUseCase, taskExecutor);
    }
}
