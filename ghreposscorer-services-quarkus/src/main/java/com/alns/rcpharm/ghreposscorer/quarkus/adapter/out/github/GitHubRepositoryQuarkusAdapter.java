package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.domain.port.out.ScoreConfigStoragePort;
import com.alns.rcpharm.ghreposscorer.domain.util.GitHubLinkHeaderParser;
import com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.dto.GitHubRepositoryDto;
import com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.dto.GitHubSearchResponseDto;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import io.smallrye.faulttolerance.api.RateLimit;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.net.URI;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GitHubRepositoryQuarkusAdapter implements GitHubRepositoryPort {

    private static final Logger log = Logger.getLogger(GitHubRepositoryQuarkusAdapter.class);

    @RestClient
    GitHubRestClient gitHubRestClient;

    @Inject
    ScoreConfigStoragePort scoreConfigStoragePort;

    @Override
    @CacheResult(cacheName = "github-repositories")
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10000, delayUnit = ChronoUnit.MILLIS)
    @RateLimit(value = 10, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Fallback(fallbackMethod = "fetchGitHubRepositoriesFallback")
    public List<GitHubRepository> fetchGitHubRepositories(String language, LocalDate createdAfter) {
        List<GitHubRepository> accumulated = new ArrayList<>();
        try {
            fetchPageStream(language, createdAfter, accumulated::addAll, () -> false);
        } catch (Exception e) {
            log.warn("GitHub API request for language '" + language + "' returned error: " + e.getMessage() + ". Returning accumulated items.");
        }
        return accumulated;
    }

    public List<GitHubRepository> fetchGitHubRepositoriesFallback(String language, LocalDate createdAfter) {
        log.warn("Fallback triggered for fetchGitHubRepositories in Quarkus for language: " + language);
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

        log.info("Fetching GitHub repositories for query: " + query);
        int pageCount = 0;

        try (Response response = gitHubRestClient.searchRepositories(
                query, "stars", "desc", 100, "alns-rcpharm-ghrepos-scorer-quarkus", authHeader)) {

            if (response.getStatus() == 429 || response.getStatus() == 403) {
                throw new com.alns.rcpharm.ghreposscorer.domain.exception.GitHubRateLimitException("GitHub API Rate Limit / Access Limit exceeded (" + response.getStatus() + ")");
            }

            pageCount++;
            if (response.getStatus() == 200) {
                GitHubSearchResponseDto dto = response.readEntity(GitHubSearchResponseDto.class);
                if (dto != null && dto.getItems() != null) {
                    List<GitHubRepository> pageItems = dto.getItems().stream()
                            .map(this::mapToDomain)
                            .toList();
                    if (!pageItems.isEmpty() && !isCancelled.getAsBoolean()) {
                        pageConsumer.accept(pageItems);
                    }
                }

                String linkHeader = response.getHeaderString("link");
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

                    URI nextUri = nextUriOpt.get();
                    log.info("Fetching next page " + (pageCount + 1) + " from URI: " + nextUri);

                    try (Response nextResponse = gitHubRestClient.searchRepositoriesByUri(
                            nextUri, "alns-rcpharm-ghrepos-scorer-quarkus", authHeader)) {

                        if (nextResponse.getStatus() == 429 || nextResponse.getStatus() == 403) {
                            throw new com.alns.rcpharm.ghreposscorer.domain.exception.GitHubRateLimitException("GitHub API Rate Limit / Access Limit exceeded on next page (" + nextResponse.getStatus() + ")");
                        }

                        pageCount++;
                        if (nextResponse.getStatus() == 200) {
                            GitHubSearchResponseDto nextDto = nextResponse.readEntity(GitHubSearchResponseDto.class);
                            if (nextDto != null && nextDto.getItems() != null) {
                                List<GitHubRepository> pageItems = nextDto.getItems().stream()
                                        .map(this::mapToDomain)
                                        .toList();
                                if (!pageItems.isEmpty() && !isCancelled.getAsBoolean()) {
                                    pageConsumer.accept(pageItems);
                                }
                            }
                        }

                        linkHeader = nextResponse.getHeaderString("link");
                        if (linkHeader == null) {
                            linkHeader = nextResponse.getHeaderString("Link");
                        }
                        nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);
                    }
                }
            }
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
