package com.alns.rcpharm.ghreposscorer.domain.service;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.ListScoredGHReposRankingStreamUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Domain Service implementing repository popularity scoring logic - in a Streamlined way
 */
public class ListScoredGHReposRankingStreamService extends AbstractCalculatedGHReposScore implements ListScoredGHReposRankingStreamUseCase {

    protected ListScoredGHReposRankingStreamService() {
        super();
    }

    public ListScoredGHReposRankingStreamService(GitHubRepositoryPort gitHubRepositoryPort,
                                                 UpdateScoreConfigUseCase updateScoreConfigUseCase) {
        super(gitHubRepositoryPort, updateScoreConfigUseCase);
    }

    public ListScoredGHReposRankingStreamService(GitHubRepositoryPort gitHubRepositoryPort,
                                                 UpdateScoreConfigUseCase updateScoreConfigUseCase,
                                                 Clock clock) {
        super(gitHubRepositoryPort, updateScoreConfigUseCase, clock);
    }

    @Override
    public Flow.Publisher<PopularityScore> getPopularRepositoriesStream(String language, LocalDate createdAfter, int limit) {
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(createdAfter, "createdAfter must not be null");

        return subscriber -> {
            if (subscriber == null) return;

            Flow.Publisher<List<GitHubRepository>> pageStream = gitHubRepositoryPort.fetchGitHubRepositoriesPageStream(language, createdAfter);

            pageStream.subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription upstreamSubscription;
                private final List<PopularityScore> accumulatedScores = new ArrayList<>();
                private final ScoreConfig config = updateScoreConfigUseCase.getCurrentConfig();
                private final Instant now = clock.instant();

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.upstreamSubscription = subscription;
                    subscriber.onSubscribe(new Flow.Subscription() {
                        @Override
                        public void request(long n) {
                            if (upstreamSubscription != null) {
                                upstreamSubscription.request(n);
                            }
                        }

                        @Override
                        public void cancel() {
                            if (upstreamSubscription != null) {
                                upstreamSubscription.cancel();
                            }
                        }
                    });
                }

                @Override
                public void onNext(List<GitHubRepository> pageRepos) {
                    if (pageRepos == null || pageRepos.isEmpty()) return;

                    List<PopularityScore> pageScores = pageRepos.stream()
                            .map(repo -> calculatePopularityScore(repo, config, now))
                            .sorted(Comparator.comparingDouble(PopularityScore::score).reversed())
                            .toList();

                    int effectiveLimit = limit > 0 ? limit : Integer.MAX_VALUE;
                    List<PopularityScore> itemsToEmit = pageScores.stream()
                            .limit(effectiveLimit)
                            .toList();

                    for (PopularityScore score : itemsToEmit) {
                        subscriber.onNext(score);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    subscriber.onError(throwable);
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                }
            });
        };
    }

}
