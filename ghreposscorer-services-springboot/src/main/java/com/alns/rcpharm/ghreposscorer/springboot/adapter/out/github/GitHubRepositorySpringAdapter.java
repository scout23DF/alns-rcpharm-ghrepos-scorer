package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import com.alns.rcpharm.ghreposscorer.domain.util.GitHubLinkHeaderParser;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubRepositoryDto;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubSearchResponseDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
        try {
            fetchPageStream(language, createdAfter, accumulated::addAll, () -> false);
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
    public java.util.concurrent.Flow.Publisher<List<GitHubRepository>> fetchGitHubRepositoriesPageStream(String language, LocalDate createdAfter) {
        return subscriber -> {
            if (subscriber == null) return;
            subscriber.onSubscribe(new java.util.concurrent.Flow.Subscription() {
                private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
                private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);

                @Override
                public void request(long n) {
                    if (n <= 0 || cancelled.get() || !started.compareAndSet(false, true)) {
                        return;
                    }

                    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        if (contextClassLoader != null) {
                            Thread.currentThread().setContextClassLoader(contextClassLoader);
                        }
                        try {
                            fetchPageStream(language, createdAfter, pageItems -> {
                                if (!cancelled.get()) {
                                    subscriber.onNext(pageItems);
                                }
                            }, cancelled::get);

                            if (!cancelled.get()) {
                                subscriber.onComplete();
                            }
                        } catch (feign.FeignException fe) {
                            if (fe.status() == 429 || fe.status() == 403) {
                                if (!cancelled.get()) {
                                    subscriber.onError(new com.alns.rcpharm.ghreposscorer.domain.exception.GitHubRateLimitException(
                                            "GitHub API Rate Limit / Access Limit exceeded (" + fe.status() + ")"));
                                }
                            } else if (!cancelled.get()) {
                                subscriber.onError(fe);
                            }
                        } catch (Throwable t) {
                            if (!cancelled.get()) {
                                subscriber.onError(t);
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

    private void fetchPageStream(String language, LocalDate createdAfter,
                                 java.util.function.Consumer<List<GitHubRepository>> pageConsumer,
                                 java.util.function.BooleanSupplier isCancelled) {
        String query = String.format("language:%s created:>%s", language, createdAfter.toString());
        String token = System.getenv("GITHUB_TOKEN");
        String authHeader = (token != null && !token.isBlank()) ? "Bearer " + token : null;

        ScoreConfig config = scoreConfigStoragePort != null ? scoreConfigStoragePort.loadConfig() : null;
        boolean handlePagination = config != null ? Boolean.TRUE.equals(config.shouldHandleGHApiPagination()) : true;
        int maxPages = config != null && config.maxPagesToFetch() != null ? config.maxPagesToFetch() : 5;
        Long delay = config != null ? config.delayBetweenGHApiRequestsMillis() : null;

        log.info("Fetching GitHub repositories for query: {}", query);
        int pageCount = 0;

        ResponseEntity<GitHubSearchResponseDto> responseEntity = gitHubFeignClient.searchRepositories(
                query, "stars", "desc", 100, 1, "alns-rcpharm-ghrepos-scorer-springboot", authHeader
        );
        pageCount++;

        if (responseEntity.getBody() != null && responseEntity.getBody().getItems() != null) {
            List<GitHubRepository> pageItems = responseEntity.getBody().getItems().stream()
                    .map(this::mapToDomain)
                    .toList();
            if (!pageItems.isEmpty() && !isCancelled.getAsBoolean()) {
                pageConsumer.accept(pageItems);
            }
        }

        String linkHeader = responseEntity.getHeaders().getFirst("link");
        Optional<URI> nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);

        while (handlePagination && nextUriOpt.isPresent() && pageCount < maxPages && !isCancelled.getAsBoolean()) {
            if (delay != null && delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            log.info("Fetching next page {} dynamically for query: {}", pageCount + 1, query);

            ResponseEntity<GitHubSearchResponseDto> nextResponse = gitHubFeignClient.searchRepositories(
                    query, "stars", "desc", 100, pageCount + 1, "alns-rcpharm-ghrepos-scorer-springboot", authHeader
            );
            pageCount++;

            if (nextResponse.getBody() != null && nextResponse.getBody().getItems() != null) {
                List<GitHubRepository> pageItems = nextResponse.getBody().getItems().stream()
                        .map(this::mapToDomain)
                        .toList();
                if (!pageItems.isEmpty() && !isCancelled.getAsBoolean()) {
                    pageConsumer.accept(pageItems);
                }
            }

            linkHeader = nextResponse.getHeaders().getFirst("link");
            if (linkHeader == null) {
                linkHeader = nextResponse.getHeaders().getFirst("Link");
            }
            nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);
        }
    }

    private GitHubRepository mapToDomain(GitHubRepositoryDto dto) {
        return new GitHubRepository(
                dto.getId() != null ? dto.getId() : "",
                dto.getName() != null ? dto.getName() : "",
                dto.getFullName() != null ? dto.getFullName() : "",
                dto.getHtmlUrl(),
                dto.getDescription(),
                dto.getLanguage(),
                dto.getStars(),
                dto.getForks(),
                dto.getPushedAt()
        );
    }
}
