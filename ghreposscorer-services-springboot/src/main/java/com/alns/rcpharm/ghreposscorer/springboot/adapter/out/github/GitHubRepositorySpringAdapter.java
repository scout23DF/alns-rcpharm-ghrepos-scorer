package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.utils.PaginationUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
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

    public GitHubRepositorySpringAdapter(GitHubFeignClient gitHubFeignClient,
                                         ScoreConfigStoragePort scoreConfigStoragePort) {
        this.gitHubFeignClient = gitHubFeignClient;
        this.scoreConfigStoragePort = scoreConfigStoragePort;
    }

    @Override
    @Cacheable(value = "github-repositories", key = "#language.toLowerCase() + '-' + #createdAfter")
    @CircuitBreaker(name = "githubApi", fallbackMethod = "fetchGitHubRepositoriesFallback")
    @RateLimiter(name = "githubApi")
    public List<GitHubRepository> fetchGitHubRepositories(String language, LocalDate createdAfter) {
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
                        try {
                            PaginationUtils.fetchGHRepositoriesPaginated(
                                    gitHubFeignClient,
                                    scoreConfig,
                                    language,
                                    createdAfter,
                                    pageItems -> {
                                        if (!cancelled.get()) {
                                            subscriber.onNext(pageItems);
                                        }
                                    }, cancelled::get
                            );
                        } catch (Throwable t) {
                            log.warn("Error fetching GitHub repositories reactively in Spring Boot: {}. Completing stream with items fetched so far.", t.getMessage());
                        } finally {
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
