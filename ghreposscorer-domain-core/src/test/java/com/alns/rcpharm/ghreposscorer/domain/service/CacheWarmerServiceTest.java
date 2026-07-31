package com.alns.rcpharm.ghreposscorer.domain.service;

import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.ListScoredGHReposRankingUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheWarmerServiceTest {

    private MockListScoredGHReposRankingUseCase calculatePopularityUseCase;
    private MockUpdateScoreConfigUseCase updateScoreConfigUseCase;
    private CacheWarmerService cacheWarmerService;

    @BeforeEach
    void setUp() {
        calculatePopularityUseCase = new MockListScoredGHReposRankingUseCase();
        updateScoreConfigUseCase = new MockUpdateScoreConfigUseCase();
        cacheWarmerService = new CacheWarmerService(calculatePopularityUseCase, updateScoreConfigUseCase);
    }

    @Test
    @DisplayName("Should iterate all popular languages from current config and call calculatePopularityUseCase")
    void testWarmCache() {
        ScoreConfig customConfig = new ScoreConfig(1.0, 1.0, 1.0, 0.01, List.of("Java", "Kotlin"), LocalDate.of(2020, 1, 1), 0L);
        updateScoreConfigUseCase.updateConfig(customConfig);

        cacheWarmerService.warmCache();

        assertThat(calculatePopularityUseCase.calledLanguages).containsExactly("Java", "Kotlin");
        assertThat(calculatePopularityUseCase.lastCreatedAfter).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(calculatePopularityUseCase.lastLimit).isEqualTo(30);
    }

    private static class MockListScoredGHReposRankingUseCase implements ListScoredGHReposRankingUseCase {
        final List<String> calledLanguages = new ArrayList<>();
        LocalDate lastCreatedAfter;
        int lastLimit;

        @Override
        public List<PopularityScore> getPopularRepositories(String language, LocalDate createdAfter, int limit) {
            calledLanguages.add(language);
            this.lastCreatedAfter = createdAfter;
            this.lastLimit = limit;
            return List.of();
        }
    }

    private static class MockUpdateScoreConfigUseCase implements UpdateScoreConfigUseCase {
        private ScoreConfig config = ScoreConfig.defaultConfig();

        @Override
        public ScoreConfig updateConfig(ScoreConfig newConfig) {
            this.config = newConfig;
            return config;
        }

        @Override
        public ScoreConfig getCurrentConfig() {
            return config;
        }
    }
}
