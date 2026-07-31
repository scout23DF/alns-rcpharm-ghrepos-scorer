package com.alns.rcpharm.ghreposscorer.domain.service;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public abstract class AbstractCalculatedGHReposScore {

    protected final GitHubRepositoryPort gitHubRepositoryPort;
    protected final UpdateScoreConfigUseCase updateScoreConfigUseCase;
    protected final Clock clock;

    protected AbstractCalculatedGHReposScore() {
        this.gitHubRepositoryPort = null;
        this.updateScoreConfigUseCase = null;
        this.clock = Clock.systemUTC();
    }

    public AbstractCalculatedGHReposScore(GitHubRepositoryPort gitHubRepositoryPort,
                                                 UpdateScoreConfigUseCase updateScoreConfigUseCase) {
        this(gitHubRepositoryPort, updateScoreConfigUseCase, Clock.systemUTC());
    }

    public AbstractCalculatedGHReposScore(GitHubRepositoryPort gitHubRepositoryPort,
                                                 UpdateScoreConfigUseCase updateScoreConfigUseCase,
                                                 Clock clock) {

        this.gitHubRepositoryPort = Objects.requireNonNull(gitHubRepositoryPort, "gitHubRepositoryPort must not be null");
        this.updateScoreConfigUseCase = Objects.requireNonNull(updateScoreConfigUseCase, "updateScoreConfigUseCase must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
