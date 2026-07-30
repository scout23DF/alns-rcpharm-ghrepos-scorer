package com.alns.rcpharm.ghreposscorer.springboot.config;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import com.alns.rcpharm.ghreposscorer.domain.service.PopularityCalculatorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicReference;

@Configuration
public class DomainConfig {

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
            ScoreConfigStoragePort scoreConfigStoragePort) {
        return new PopularityCalculatorService(gitHubRepositoryPort, scoreConfigStoragePort);
    }
}
