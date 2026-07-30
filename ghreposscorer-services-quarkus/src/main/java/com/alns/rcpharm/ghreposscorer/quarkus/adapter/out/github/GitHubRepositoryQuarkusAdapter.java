package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.port.out.GitHubRepositoryPort;
import com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.dto.GitHubRepositoryDto;
import com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.dto.GitHubSearchResponseDto;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import io.smallrye.faulttolerance.api.RateLimit;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class GitHubRepositoryQuarkusAdapter implements GitHubRepositoryPort {

    private static final Logger log = Logger.getLogger(GitHubRepositoryQuarkusAdapter.class);

    @RestClient
    GitHubRestClient gitHubRestClient;

    @Override
    @CacheResult(cacheName = "github-repositories")
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10000, delayUnit = ChronoUnit.MILLIS)
    @RateLimit(value = 10, window = 1, windowUnit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "fetchGitHubRepositoriesFallback")
    public List<GitHubRepository> fetchGitHubRepositories(String language, LocalDate createdAfter) {
        String query = String.format("language:%s created:>%s", language, createdAfter.toString());
        String token = System.getenv("GITHUB_TOKEN");
        String authHeader = (token != null && !token.isBlank()) ? "Bearer " + token : null;

        log.info("Fetching GitHub repositories for query: " + query);
        GitHubSearchResponseDto response = gitHubRestClient.searchRepositories(
                query, "stars", "desc", 100, "alns-rcpharm-ghrepos-scorer-quarkus", authHeader
        );

        if (response == null || response.getItems() == null) {
            return Collections.emptyList();
        }

        return response.getItems().stream()
                .map(this::mapToDomain)
                .toList();
    }

    public List<GitHubRepository> fetchGitHubRepositoriesFallback(String language, LocalDate createdAfter) {
        log.warn("Fallback triggered for fetchGitHubRepositories in Quarkus for language: " + language);
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
