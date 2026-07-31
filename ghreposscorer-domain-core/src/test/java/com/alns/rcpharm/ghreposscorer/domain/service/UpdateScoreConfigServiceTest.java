package com.alns.rcpharm.ghreposscorer.domain.service;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.WarmCacheUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.out.CacheInvalidatorPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateScoreConfigServiceTest {

    private InMemoryScoreConfigStorage scoreConfigStorage;
    private MockCacheInvalidator cacheInvalidator;
    private MockWarmCacheUseCase warmCacheUseCase;
    private UpdateScoreConfigService service;

    @BeforeEach
    void setUp() {
        scoreConfigStorage = new InMemoryScoreConfigStorage();
        cacheInvalidator = new MockCacheInvalidator();
        warmCacheUseCase = new MockWarmCacheUseCase();
        service = new UpdateScoreConfigService(scoreConfigStorage, cacheInvalidator, warmCacheUseCase);
    }

    @Test
    @DisplayName("Should return default configuration when no config is stored")
    void testGetCurrentConfigDefault() {
        ScoreConfig current = service.getCurrentConfig();
        assertThat(current).isNotNull();
        assertThat(current).isEqualTo(ScoreConfig.defaultConfig());
    }

    @Test
    @DisplayName("Should update configuration dynamically, invalidate cache, and run cache warmer")
    void testUpdateConfig() {
        ScoreConfig newConfig = new ScoreConfig(2.0, 2.0, 1.0, 0.05, List.of("Rust", "Zig"), LocalDate.of(2022, 1, 1));
        ScoreConfig updated = service.updateConfig(newConfig);

        assertThat(updated).isEqualTo(newConfig);
        assertThat(service.getCurrentConfig()).isEqualTo(newConfig);
        assertThat(service.getCurrentConfig().popularLanguages()).containsExactly("Rust", "Zig");
        assertThat(service.getCurrentConfig().defaultCreatedAfter()).isEqualTo(LocalDate.of(2022, 1, 1));
        assertThat(cacheInvalidator.invalidated).isTrue();
        assertThat(warmCacheUseCase.warmedAsync).isTrue();
    }

    private static class MockCacheInvalidator implements CacheInvalidatorPort {
        boolean invalidated = false;

        @Override
        public void invalidateCache() {
            this.invalidated = true;
        }
    }

    private static class MockWarmCacheUseCase implements WarmCacheUseCase {
        boolean warmedAsync = false;

        @Override
        public void warmCache() {
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> warmCacheAsync() {
            this.warmedAsync = true;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    private static class InMemoryScoreConfigStorage implements ScoreConfigStoragePort {
        private ScoreConfig config;

        @Override
        public ScoreConfig loadConfig() {
            return config;
        }

        @Override
        public void saveConfig(ScoreConfig config) {
            this.config = config;
        }
    }
}
