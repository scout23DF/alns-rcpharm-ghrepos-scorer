package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils.PaginationUtils;
import com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils.SimpleCacheManagerProxy;
import io.quarkus.cache.CacheResult;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class GitHubRepositoryQuarkusAdapter implements GitHubRepositoryPort {

    private static final Logger log = Logger.getLogger(GitHubRepositoryQuarkusAdapter.class);

    @RestClient
    GitHubRestClient gitHubRestClient;

    @Inject
    ScoreConfigStoragePort scoreConfigStoragePort;

    @Inject
    SimpleCacheManagerProxy simpleCacheManagerProxy;

    @Override
    @CacheResult(cacheName = "github-repositories")
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10000, delayUnit = ChronoUnit.MILLIS)
    @RateLimit(value = 10, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Fallback(fallbackMethod = "fetchGitHubRepositoriesFallback")
    public List<GitHubRepository> fetchGitHubRepositories(String language, LocalDate createdAfter) {
        List<GitHubRepository> accumulated = new ArrayList<>();
        ScoreConfig scoreConfig = scoreConfigStoragePort != null ? scoreConfigStoragePort.loadConfig() : null;

        try {
            PaginationUtils.fetchGHRepositoriesPaginated(
                    gitHubRestClient,
                    scoreConfig,
                    language,
                    createdAfter,
                    accumulated::addAll,
                    () -> false);

        } catch (Exception e) {
            log.warn("GitHub API request for language '" + language + "' returned error: " + e.getMessage() + ". Returning accumulated items.");
        }
        return accumulated;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Flow.Publisher<List<GitHubRepository>> fetchGitHubRepositoriesPageStream(String language, LocalDate createdAfter) {
        ScoreConfig scoreConfig = scoreConfigStoragePort != null ? scoreConfigStoragePort.loadConfig() : null;

        if (simpleCacheManagerProxy.containsKey(language.toLowerCase(), createdAfter.toString())) {
            log.info("Attempting to retrieve from CacheManager in Quarkus (language=" + language + ", createdAfter=" + createdAfter + ")");
            return buildFlowPublisherFromCache(scoreConfig, language, createdAfter);
        } else {
            log.info("Cache MISS for reactive page stream in Quarkus (language=" + language + ", createdAfter=" + createdAfter + ")");
            return buildFlowPublisherFromFetchAPI(scoreConfig, language, createdAfter);
        }

    }

    private Flow.Publisher<List<GitHubRepository>> buildFlowPublisherFromCache(
            ScoreConfig scoreConfig,
            String language,
            LocalDate createdAfter
    ) {

        List<GitHubRepository> cachedList = simpleCacheManagerProxy.get(language.toLowerCase(), createdAfter.toString());

        log.info("Cache HIT for reactive page stream in Quarkus (language=" + language + ", createdAfter=" + createdAfter + ") with " + cachedList.size() + " items");
        final List<GitHubRepository> finalCachedList = cachedList;

        return subscriber -> {
            if (subscriber == null) {
                return;
            }
            subscriber.onSubscribe(new Flow.Subscription() {
                private final AtomicBoolean cancelled = new AtomicBoolean(false);
                private final AtomicBoolean started = new AtomicBoolean(false);

                @Override
                public void request(long n) {
                    if (n <= 0 || cancelled.get() || !started.compareAndSet(false, true)) {
                        return;
                    }

                    subscriber.onNext(finalCachedList);

                    if (!cancelled.get()) {
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        };

    }

    private Flow.Publisher<List<GitHubRepository>> buildFlowPublisherFromFetchAPI(
            ScoreConfig scoreConfig,
            String language,
            LocalDate createdAfter) {

        return subscriber -> {
            if (subscriber == null) return;
            subscriber.onSubscribe(new java.util.concurrent.Flow.Subscription() {
                private final AtomicBoolean cancelled = new AtomicBoolean(false);
                private final AtomicBoolean started = new AtomicBoolean(false);

                @Override
                public void request(long n) {
                    if (n <= 0 || cancelled.get() || !started.compareAndSet(false, true)) {
                        return;
                    }

                    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                    CompletableFuture.runAsync(() -> {
                        if (contextClassLoader != null) {
                            Thread.currentThread().setContextClassLoader(contextClassLoader);
                        }
                        List<GitHubRepository> accumulatedForCache = new ArrayList<>();
                        try {
                            PaginationUtils.fetchGHRepositoriesPaginated(
                                    gitHubRestClient,
                                    scoreConfig,
                                    language,
                                    createdAfter,
                                    pageItems -> {
                                        if (!cancelled.get()) {
                                            accumulatedForCache.addAll(pageItems);
                                            subscriber.onNext(pageItems);
                                        }
                                    }, cancelled::get
                            );
                        } catch (Throwable t) {
                            log.warn("Error fetching GitHub repositories reactively in Quarkus: " + t.getMessage() + ". Completing stream with items fetched so far.");
                        } finally {
                            simpleCacheManagerProxy.put(accumulatedForCache, language.toLowerCase(), createdAfter.toString());
                            log.info("Populated cache for reactive stream in Quarkus (language=" + language + ", createdAfter=" + createdAfter + ") with " + accumulatedForCache.size() + " items");
                            if (!cancelled.get()) {
                                subscriber.onComplete();
                            }
                        }
                    });
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        };

    }

    public List<GitHubRepository> fetchGitHubRepositoriesFallback(String language, LocalDate createdAfter) {
        log.warn("Fallback triggered for fetchGitHubRepositories in Quarkus for language: " + language);
        return Collections.emptyList();
    }

}
