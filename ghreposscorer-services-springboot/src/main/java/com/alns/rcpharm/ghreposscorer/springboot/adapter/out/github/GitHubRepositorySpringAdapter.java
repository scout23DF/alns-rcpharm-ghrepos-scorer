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
        String query = String.format("language:%s created:>%s", language, createdAfter.toString());
        String token = System.getenv("GITHUB_TOKEN");
        String authHeader = (token != null && !token.isBlank()) ? "Bearer " + token : null;

        ScoreConfig config = scoreConfigStoragePort != null ? scoreConfigStoragePort.loadConfig() : null;
        boolean handlePagination = config != null ? Boolean.TRUE.equals(config.shouldHandleGHApiPagination()) : true;
        int maxPages = config != null && config.maxPagesToFetch() != null ? config.maxPagesToFetch() : 5;

        log.info("Fetching GitHub repositories for query: {}", query);
        List<GitHubRepository> accumulated = new ArrayList<>();
        int pageCount = 0;

        try {
            ResponseEntity<GitHubSearchResponseDto> responseEntity = gitHubFeignClient.searchRepositories(
                    query, "stars", "desc", 100, 1, "alns-rcpharm-ghrepos-scorer-springboot", authHeader
            );
            pageCount++;

            if (responseEntity.getBody() != null && responseEntity.getBody().getItems() != null) {
                responseEntity.getBody().getItems().stream().map(this::mapToDomain).forEach(accumulated::add);
            }

            String linkHeader = responseEntity.getHeaders().getFirst("link");
            Optional<URI> nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);

            while (handlePagination && nextUriOpt.isPresent() && pageCount < maxPages) {
                Long delay = config != null ? config.delayBetweenGHApiRequestsMillis() : null;
                if (delay != null && delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                URI nextUri = nextUriOpt.get();
                log.info("Fetching next page {} from URI: {}", pageCount + 1, nextUri);

                ResponseEntity<GitHubSearchResponseDto> nextResponse = gitHubFeignClient.searchRepositoriesByUri(
                        nextUri, "alns-rcpharm-ghrepos-scorer-springboot", authHeader
                );
                pageCount++;

                if (nextResponse.getBody() != null && nextResponse.getBody().getItems() != null) {
                    nextResponse.getBody().getItems().stream().map(this::mapToDomain).forEach(accumulated::add);
                }

                linkHeader = nextResponse.getHeaders().getFirst("link");
                if (linkHeader == null) {
                    linkHeader = nextResponse.getHeaders().getFirst("Link");
                }
                nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);
            }
        } catch (Exception e) {
            log.warn("GitHub API request for query '{}' returned error: {}. Returning accumulated items.", query, e.getMessage());
        }

        return accumulated;
    }

    public List<GitHubRepository> fetchGitHubRepositoriesFallback(String language, LocalDate createdAfter, Throwable t) {
        log.warn("Fallback triggered for fetchGitHubRepositories(language={}, createdAfter={}): {}", language, createdAfter, t.getMessage());
        return Collections.emptyList();
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
