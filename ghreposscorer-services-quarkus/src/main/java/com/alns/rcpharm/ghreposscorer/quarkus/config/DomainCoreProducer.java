package com.alns.rcpharm.ghreposscorer.quarkus.config;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.CalculatePopularityUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import com.alns.rcpharm.ghreposscorer.domain.service.PopularityCalculatorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;

import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class DomainCoreProducer {

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
    @Typed({CalculatePopularityUseCase.class, UpdateScoreConfigUseCase.class, PopularityCalculatorService.class})
    public PopularityCalculatorService popularityCalculatorService(
            GitHubRepositoryPort gitHubRepositoryPort,
            ScoreConfigStoragePort scoreConfigStoragePort) {
        return new PopularityCalculatorService(gitHubRepositoryPort, scoreConfigStoragePort);
    }
}
