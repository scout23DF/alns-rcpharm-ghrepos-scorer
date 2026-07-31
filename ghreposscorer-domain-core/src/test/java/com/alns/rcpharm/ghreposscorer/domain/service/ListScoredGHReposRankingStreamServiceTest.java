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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

class ListScoredGHReposRankingStreamServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-29T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    private MockUpdateScoreConfigUseCase updateScoreConfigUseCase;
    private InMemoryGitHubRepositoryPort gitHubRepositoryPort;
    private ListScoredGHReposRankingStreamService streamService;

    @BeforeEach
    void setUp() {
        updateScoreConfigUseCase = new MockUpdateScoreConfigUseCase();
        gitHubRepositoryPort = new InMemoryGitHubRepositoryPort();
        streamService = new ListScoredGHReposRankingStreamService(gitHubRepositoryPort, updateScoreConfigUseCase, fixedClock);
    }

    @Test
    @DisplayName("Should stream popular repositories via Flow.Publisher reactively")
    void testGetPopularRepositoriesStream() {
        GitHubRepository repo = new GitHubRepository("1", "repo-1", "org/repo-1", "http://github.com/org/repo-1",
                "Desc", "java", 100, 50, FIXED_NOW);
        gitHubRepositoryPort.setRepositories(List.of(repo));

        Flow.Publisher<List<PopularityScore>> publisher = streamService.getPopularRepositoriesStream("java", LocalDate.of(2020, 1, 1), 5);
        assertThat(publisher).isNotNull();

        List<List<PopularityScore>> streamed = new ArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(10);
            }

            @Override
            public void onNext(List<PopularityScore> item) {
                streamed.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        assertThat(streamed).hasSize(1);
        assertThat(streamed.get(0)).hasSize(1);
        assertThat(streamed.get(0).get(0).repository().id()).isEqualTo("1");
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

        @Override
        public Flow.Publisher<List<GitHubRepository>> fetchGitHubRepositoriesPageStream(String language, LocalDate createdAfter) {
            return subscriber -> {
                if (subscriber == null) return;
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                        subscriber.onNext(repositories);
                        subscriber.onComplete();
                    }

                    @Override
                    public void cancel() {
                    }
                });
            };
        }
    }
}
