package com.alns.rcpharm.ghreposscorer.domain.service;

import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.model.Repository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;

import com.alns.rcpharm.ghreposscorer.domain.service.PopularityCalculatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PopularityCalculatorServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-29T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    private InMemoryScoreConfigStorage scoreConfigStorage;
    private InMemoryGitHubRepositoryPort gitHubRepositoryPort;
    private PopularityCalculatorService calculatorService;

    @BeforeEach
    void setUp() {
        scoreConfigStorage = new InMemoryScoreConfigStorage();
        gitHubRepositoryPort = new InMemoryGitHubRepositoryPort();
        calculatorService = new PopularityCalculatorService(gitHubRepositoryPort, scoreConfigStorage, fixedClock);
    }

    @Test
    @DisplayName("Should calculate score correctly using default weights and zero days decay")
    void testCalculateScoreZeroDaysDecay() {
        // Repo pushed today (0 days since last push)
        Repository repo = new Repository("1", "repo-1", "org/repo-1", "http://github.com/org/repo-1",
                "Desc", "java", 100, 50, FIXED_NOW);

        ScoreConfig config = ScoreConfig.defaultConfig(); // wStars=1.0, wForks=1.2, wRecency=0.8, lambda=0.01
        // Score = (1.0 * 100) + (1.2 * 50) + (0.8 * (100 / (1 + 0.01 * 0))) = 100 + 60 + 80 = 240.0
        PopularityScore score = calculatorService.calculatePopularityScore(repo, config, FIXED_NOW);

        assertThat(score.score()).isEqualTo(240.0);
    }

    @Test
    @DisplayName("Should apply recency decay when repository was pushed 100 days ago")
    void testCalculateScoreWithRecencyDecay() {
        Instant pushed100DaysAgo = FIXED_NOW.minus(100, ChronoUnit.DAYS);
        Repository repo = new Repository("2", "repo-2", "org/repo-2", "http://github.com/org/repo-2",
                "Desc", "kotlin", 100, 50, pushed100DaysAgo);

        ScoreConfig config = ScoreConfig.defaultConfig();
        // recencyFactor = 100 / (1 + 0.01 * 100) = 100 / 2 = 50.0
        // Score = (1.0 * 100) + (1.2 * 50) + (0.8 * 50.0) = 100 + 60 + 40 = 200.0
        PopularityScore score = calculatorService.calculatePopularityScore(repo, config, FIXED_NOW);

        assertThat(score.score()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("Should sort repositories descending by score and limit output")
    void testGetPopularRepositoriesSortingAndLimit() {
        Repository repoLow = new Repository("1", "low", "org/low", "http://gh/low", "Low", "java", 10, 5, FIXED_NOW);
        Repository repoHigh = new Repository("2", "high", "org/high", "http://gh/high", "High", "java", 500, 200, FIXED_NOW);

        gitHubRepositoryPort.setRepositories(List.of(repoLow, repoHigh));

        List<PopularityScore> results = calculatorService.getPopularRepositories("java", LocalDate.of(2020, 1, 1), 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).repository().id()).isEqualTo("2");
    }

    @Test
    @DisplayName("Should update configuration dynamically")
    void testUpdateConfig() {
        ScoreConfig newConfig = new ScoreConfig(2.0, 2.0, 1.0, 0.05);
        ScoreConfig updated = calculatorService.updateConfig(newConfig);

        assertThat(updated).isEqualTo(newConfig);
        assertThat(calculatorService.getCurrentConfig()).isEqualTo(newConfig);
    }

    // Static test helpers
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

    private static class InMemoryGitHubRepositoryPort implements GitHubRepositoryPort {
        private List<Repository> repositories = List.of();

        public void setRepositories(List<Repository> repositories) {
            this.repositories = repositories;
        }

        @Override
        public List<Repository> fetchRepositories(String language, LocalDate createdAfter) {
            return repositories;
        }
    }
}
