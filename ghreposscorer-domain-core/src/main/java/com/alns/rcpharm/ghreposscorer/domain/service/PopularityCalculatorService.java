package com.alns.rcpharm.ghreposscorer.domain.service;

import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.CalculatePopularityUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Domain Service implementing repository popularity scoring logic and dynamic configuration management.
 */
public class PopularityCalculatorService implements CalculatePopularityUseCase, UpdateScoreConfigUseCase {

    private final GitHubRepositoryPort gitHubRepositoryPort;
    private final ScoreConfigStoragePort scoreConfigStoragePort;
    private final Clock clock;

    public PopularityCalculatorService(GitHubRepositoryPort gitHubRepositoryPort,
                                       ScoreConfigStoragePort scoreConfigStoragePort) {
        this(gitHubRepositoryPort, scoreConfigStoragePort, Clock.systemUTC());
    }

    public PopularityCalculatorService(GitHubRepositoryPort gitHubRepositoryPort,
                                       ScoreConfigStoragePort scoreConfigStoragePort,
                                       Clock clock) {
        this.gitHubRepositoryPort = Objects.requireNonNull(gitHubRepositoryPort, "gitHubRepositoryPort must not be null");
        this.scoreConfigStoragePort = Objects.requireNonNull(scoreConfigStoragePort, "scoreConfigStoragePort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public List<PopularityScore> getPopularRepositories(String language, LocalDate createdAfter, int limit) {
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(createdAfter, "createdAfter must not be null");

        List<GitHubRepository> repositories = gitHubRepositoryPort.fetchGitHubRepositories(language, createdAfter);
        ScoreConfig config = getCurrentConfig();
        Instant now = clock.instant();

        return repositories.stream()
                .map(repo -> calculatePopularityScore(repo, config, now))
                .sorted(Comparator.comparingDouble(PopularityScore::score).reversed())
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .toList();
    }

    @Override
    public ScoreConfig updateConfig(ScoreConfig newConfig) {
        Objects.requireNonNull(newConfig, "newConfig must not be null");
        scoreConfigStoragePort.saveConfig(newConfig);
        return newConfig;
    }

    @Override
    public ScoreConfig getCurrentConfig() {
        ScoreConfig loaded = scoreConfigStoragePort.loadConfig();
        return loaded != null ? loaded : ScoreConfig.defaultConfig();
    }

    /**
     * Calculates the popularity score using weighted metrics and recency decay factor:
     * Score = (wStars * Stars) + (wForks * Forks) + (wRecency * (100 / (1 + lambda * DaysSinceLastPush)))
     */
    public PopularityScore calculatePopularityScore(GitHubRepository repository, ScoreConfig config, Instant now) {
        long daysSinceLastPush = 0;
        if (repository.pushedAt() != null && repository.pushedAt().isBefore(now)) {
            daysSinceLastPush = Duration.between(repository.pushedAt(), now).toDays();
        }

        double recencyFactor = 100.0 / (1.0 + (config.decayLambda() * daysSinceLastPush));
        double score = (config.wStars() * repository.stars())
                + (config.wForks() * repository.forks())
                + (config.wRecency() * recencyFactor);

        return new PopularityScore(repository, score, now);
    }
}
