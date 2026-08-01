package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.utils.PaginationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GitHubRepositorySpringAdapter implements GitHubRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositorySpringAdapter.class);
    private final GitHubFeignClient gitHubFeignClient;
    private final ScoreConfigStoragePort scoreConfigStoragePort;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public GitHubRepositorySpringAdapter(GitHubFeignClient gitHubFeignClient,
                                         ScoreConfigStoragePort scoreConfigStoragePort,
                                         @Autowired(required = false) CacheManager cacheManager,
                                         @Autowired(required = false) ObjectMapper objectMapper) {
        this.gitHubFeignClient = gitHubFeignClient;
        this.scoreConfigStoragePort = scoreConfigStoragePort;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    @CircuitBreaker(name = "githubApi", fallbackMethod = "fetchGitHubRepositoriesFallback")
    @RateLimiter(name = "githubApi")
    public List<GitHubRepository> fetchGitHubRepositories(String language, LocalDate createdAfter) {
        String cacheKey = language.toLowerCase() + "::" + createdAfter;
        Cache cache = cacheManager != null ? cacheManager.getCache("github-repositories") : null;
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null && wrapper.get() instanceof List<?> rawList && !rawList.isEmpty()) {
                log.info("Cache HIT for fetchGitHubRepositories in Spring Boot (language={}, createdAfter={})", language, createdAfter);
                return rawList.stream()
                        .map(item -> item instanceof GitHubRepository gh ? gh : objectMapper.convertValue(item, GitHubRepository.class))
                        .toList();
            }
        }

        List<GitHubRepository> accumulated = new ArrayList<>();
        ScoreConfig scoreConfig = scoreConfigStoragePort != null ? scoreConfigStoragePort.loadConfig() : null;
        try {
            PaginationUtils.fetchGHRepositoriesPaginated(
                    gitHubFeignClient,
                    scoreConfig,
                    language,
                    createdAfter,
                    accumulated::addAll,
                    () -> false
            );
            if (cache != null && !accumulated.isEmpty()) {
                cache.put(cacheKey, accumulated);
            }
        } catch (Exception e) {
            log.warn("GitHub API request for language '{}' returned error: {}. Returning accumulated items.", language, e.getMessage());
        }
        return accumulated;
    }

    public List<GitHubRepository> fetchGitHubRepositoriesFallback(String language, LocalDate createdAfter, Throwable t) {
        log.warn("Fallback triggered for fetchGitHubRepositories(language={}, createdAfter={}): {}", language, createdAfter, t.getMessage());
        return Collections.emptyList();
    }

    @Override
    public Flow.Publisher<List<GitHubRepository>> fetchGitHubRepositoriesPageStream(String language, LocalDate createdAfter) {
        ScoreConfig scoreConfig = scoreConfigStoragePort != null ? scoreConfigStoragePort.loadConfig() : null;
        String cacheKey = language.toLowerCase() + "::" + createdAfter;
        Cache cache = cacheManager != null ? cacheManager.getCache("github-repositories") : null;

        List<GitHubRepository> cachedList = null;
        if (cache != null) {
            Cache.ValueWrapper valueWrapper = cache.get(cacheKey);
            if (valueWrapper != null && valueWrapper.get() instanceof List<?> rawList) {
                cachedList = rawList.stream()
                        .map(item -> item instanceof GitHubRepository gh ? gh : objectMapper.convertValue(item, GitHubRepository.class))
                        .toList();
            }
        }

        if (cachedList != null && !cachedList.isEmpty()) {
            log.info("Cache HIT for reactive page stream in Spring Boot (language={}, createdAfter={}) with {} items", language, createdAfter, cachedList.size());
            final List<GitHubRepository> finalCachedList = cachedList;
            return subscriber -> {
                if (subscriber == null) return;
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
            subscriber.onSubscribe(new Flow.Subscription() {
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
                                    gitHubFeignClient,
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
                            log.warn("Error fetching GitHub repositories reactively in Spring Boot: {}. Completing stream with items fetched so far.", t.getMessage());
                        } finally {
                            if (cache != null && !accumulatedForCache.isEmpty()) {
                                cache.put(cacheKey, accumulatedForCache);
                                log.info("Populated cache for reactive stream in Spring Boot (language={}, createdAfter={}) with {} items", language, createdAfter, accumulatedForCache.size());
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
}
