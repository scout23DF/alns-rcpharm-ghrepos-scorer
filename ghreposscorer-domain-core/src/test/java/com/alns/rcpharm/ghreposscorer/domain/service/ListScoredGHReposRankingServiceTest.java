package com.alns.rcpharm.ghreposscorer.domain.service;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
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

class ListScoredGHReposRankingServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-29T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    private MockUpdateScoreConfigUseCase updateScoreConfigUseCase;
    private InMemoryGitHubRepositoryPort gitHubRepositoryPort;
    private ListScoredGHReposRankingService rankingService;

    @BeforeEach
    void setUp() {
        updateScoreConfigUseCase = new MockUpdateScoreConfigUseCase();
        gitHubRepositoryPort = new InMemoryGitHubRepositoryPort();
        rankingService = new ListScoredGHReposRankingService(gitHubRepositoryPort, updateScoreConfigUseCase, fixedClock);
    }

    @Test
    @DisplayName("Should calculate score correctly using default weights and zero days decay")
    void testCalculateScoreZeroDaysDecay() {
        GitHubRepository repo = new GitHubRepository("1", "repo-1", "org/repo-1", "http://github.com/org/repo-1",
                "Desc", "java", 100, 50, FIXED_NOW);

        ScoreConfig config = ScoreConfig.defaultConfig();
        PopularityScore score = rankingService.calculatePopularityScore(repo, config, FIXED_NOW);

        assertThat(score.score()).isEqualTo(240.0);
    }

    @Test
    @DisplayName("Should apply recency decay when repository was pushed 100 days ago")
    void testCalculateScoreWithRecencyDecay() {
        Instant pushed100DaysAgo = FIXED_NOW.minus(100, ChronoUnit.DAYS);
        GitHubRepository repo = new GitHubRepository("2", "repo-2", "org/repo-2", "http://github.com/org/repo-2",
                "Desc", "kotlin", 100, 50, pushed100DaysAgo);

        ScoreConfig config = ScoreConfig.defaultConfig();
        PopularityScore score = rankingService.calculatePopularityScore(repo, config, FIXED_NOW);

        assertThat(score.score()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("Should sort repositories descending by score and limit output")
    void testGetPopularRepositoriesSortingAndLimit() {
        GitHubRepository repoLow = new GitHubRepository("1", "low", "org/low", "http://gh/low", "Low", "java", 10, 5, FIXED_NOW);
        GitHubRepository repoHigh = new GitHubRepository("2", "high", "org/high", "http://gh/high", "High", "java", 500, 200, FIXED_NOW);

        gitHubRepositoryPort.setRepositories(List.of(repoLow, repoHigh));

        List<PopularityScore> results = rankingService.getPopularRepositories("java", LocalDate.of(2020, 1, 1), 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).repository().id()).isEqualTo("2");
    }

    private static class MockUpdateScoreConfigUseCase implements UpdateScoreConfigUseCase {
        private ScoreConfig config = ScoreConfig.defaultConfig();

        @Override
        public ScoreConfig updateConfig(ScoreConfig newConfig) {
            this.config = newConfig;
            return newConfig;
        }

        @Override
        public ScoreConfig getCurrentConfig() {
            return config;
        }
    }

    private static class InMemoryGitHubRepositoryPort implements GitHubRepositoryPort {
        private List<GitHubRepository> repositories = List.of();

        public void setRepositories(List<GitHubRepository> repositories) {
            this.repositories = repositories;
        }

        @Override
        public List<GitHubRepository> fetchGitHubRepositories(String language, LocalDate createdAfter) {
            return repositories;
        }
    }
}
