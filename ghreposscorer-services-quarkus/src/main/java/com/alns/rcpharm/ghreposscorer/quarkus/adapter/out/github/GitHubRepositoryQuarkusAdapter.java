package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils.PaginationUtils;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CacheResult;
import io.quarkus.cache.CompositeCacheKey;
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
import java.util.Optional;
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
    CacheManager cacheManager;

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
        List<GitHubRepository> cachedList = null;
        CompositeCacheKey key = new CompositeCacheKey(language, createdAfter);

        Cache cache = Optional.ofNullable(cacheManager)
                .flatMap(cm -> cm.getCache("github-repositories"))
                .orElse(null);

        if (cache != null) {

            try {
                Object raw = cache.get(key, k -> null).await().indefinitely();
                if (raw instanceof List) {
                    cachedList = (List<GitHubRepository>) raw;
                }
            } catch (Exception ignored) {
            }

        }

        if (cachedList != null && !cachedList.isEmpty()) {
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
                        int pageSize = 100;
                        for (int i = 0; i < finalCachedList.size() && !cancelled.get(); i += pageSize) {
                            List<GitHubRepository> pageChunk = finalCachedList.subList(i, Math.min(i + pageSize, finalCachedList.size()));
                            subscriber.onNext(pageChunk);
                        }
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
                            if (cacheManager != null && !accumulatedForCache.isEmpty()) {
                                cacheManager.getCache("github-repositories").ifPresent(cache -> {
                                    try {
                                        io.quarkus.cache.CompositeCacheKey key = new io.quarkus.cache.CompositeCacheKey(language, createdAfter);
                                        io.quarkus.cache.CaffeineCache caffeineCache = cache.as(io.quarkus.cache.CaffeineCache.class);
                                        caffeineCache.put(key, CompletableFuture.completedFuture(accumulatedForCache));
                                        log.info("Populated cache for reactive stream in Quarkus (language=" + language + ", createdAfter=" + createdAfter + ") with " + accumulatedForCache.size() + " items");
                                    } catch (Exception e) {
                                        log.warn("Failed to populate cache in Quarkus: " + e.getMessage());
                                    }
                                });
                            }
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
