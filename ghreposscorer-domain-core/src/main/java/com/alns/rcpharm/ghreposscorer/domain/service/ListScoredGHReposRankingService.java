package com.alns.rcpharm.ghreposscorer.domain.service;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.ListScoredGHReposRankingUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Domain Service implementing repository popularity scoring logic
 */
public class ListScoredGHReposRankingService extends AbstractCalculatedGHReposScore implements ListScoredGHReposRankingUseCase {

    protected ListScoredGHReposRankingService() {
        super();
    }

    public ListScoredGHReposRankingService(GitHubRepositoryPort gitHubRepositoryPort,
                                           UpdateScoreConfigUseCase updateScoreConfigUseCase) {
        super(gitHubRepositoryPort, updateScoreConfigUseCase);
    }

    public ListScoredGHReposRankingService(GitHubRepositoryPort gitHubRepositoryPort,
                                           UpdateScoreConfigUseCase updateScoreConfigUseCase, Clock clock) {
        super(gitHubRepositoryPort, updateScoreConfigUseCase, clock);
    }

    @Override
    public List<PopularityScore> getPopularRepositories(String language, LocalDate createdAfter, int limit) {
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(createdAfter, "createdAfter must not be null");

        List<GitHubRepository> repositories = gitHubRepositoryPort.fetchGitHubRepositories(language, createdAfter);
        ScoreConfig config = updateScoreConfigUseCase.getCurrentConfig();
        Instant now = clock.instant();

        return repositories.stream()
                .map(repo -> calculatePopularityScore(repo, config, now))
                .sorted(Comparator.comparingDouble(PopularityScore::score).reversed())
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .toList();
    }

}
