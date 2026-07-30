package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubRepositoryDto;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubSearchResponseDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Component
public class GitHubRepositorySpringAdapter implements GitHubRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositorySpringAdapter.class);
    private final GitHubFeignClient gitHubFeignClient;

    public GitHubRepositorySpringAdapter(GitHubFeignClient gitHubFeignClient) {
        this.gitHubFeignClient = gitHubFeignClient;
    }

    @Override
    @Cacheable(value = "github-repositories", key = "#language.toLowerCase() + '-' + #createdAfter")
    @CircuitBreaker(name = "githubApi", fallbackMethod = "fetchGitHubRepositoriesFallback")
    @RateLimiter(name = "githubApi")
    public List<GitHubRepository> fetchGitHubRepositories(String language, LocalDate createdAfter) {
        String query = String.format("language:%s created:>%s", language, createdAfter.toString());
        String token = System.getenv("GITHUB_TOKEN");
        String authHeader = (token != null && !token.isBlank()) ? "Bearer " + token : null;

        log.info("Fetching GitHub repositories for query: {}", query);
        try {
            ResponseEntity<GitHubSearchResponseDto> responseEntity = gitHubFeignClient.searchRepositories(
                    query, "stars", "desc", 100, 1, "alns-rcpharm-ghrepos-scorer-springboot", authHeader
            );

            String linkHeader = responseEntity.getHeaders().getFirst("Link");
            if (linkHeader != null) {
                log.debug("GitHub Response Link Header: {}", linkHeader);
            }

            GitHubSearchResponseDto response = responseEntity.getBody();
            if (response == null || response.getItems() == null) {
                return Collections.emptyList();
            }

            return response.getItems().stream()
                    .map(this::mapToDomain)
                    .toList();
        } catch (Exception e) {
            log.warn("GitHub API request for query '{}' returned error: {}. Returning empty list.", query, e.getMessage());
            return Collections.emptyList();
        }
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
