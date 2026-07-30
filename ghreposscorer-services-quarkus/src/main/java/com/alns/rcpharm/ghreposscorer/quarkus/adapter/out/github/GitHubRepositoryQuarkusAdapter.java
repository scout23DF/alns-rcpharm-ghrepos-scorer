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
        String query = String.format("language:%s created:>%s", language, createdAfter.toString());
        String token = System.getenv("GITHUB_TOKEN");
        String authHeader = (token != null && !token.isBlank()) ? "Bearer " + token : null;

        ScoreConfig config = scoreConfigStoragePort != null ? scoreConfigStoragePort.loadConfig() : null;
        boolean handlePagination = config != null ? Boolean.TRUE.equals(config.shouldHandleGHApiPagination()) : true;
        int maxPages = config != null && config.maxPagesToFetch() != null ? config.maxPagesToFetch() : 5;

        log.info("Fetching GitHub repositories for query: " + query);
        List<GitHubRepository> accumulated = new ArrayList<>();
        int pageCount = 0;

        try (Response response = gitHubRestClient.searchRepositories(
                query, "stars", "desc", 100, "alns-rcpharm-ghrepos-scorer", authHeader)) {

            pageCount++;
            GitHubSearchResponseDto dto = response.readEntity(GitHubSearchResponseDto.class);
            if (dto != null && dto.getItems() != null) {
                dto.getItems().stream().map(this::mapToDomain).forEach(accumulated::add);
            }

            String linkHeader = response.getHeaderString("Link");
            Optional<URI> nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);

            while (handlePagination && nextUriOpt.isPresent() && pageCount < maxPages) {
                URI nextUri = nextUriOpt.get();
                log.info("Fetching next page " + (pageCount + 1) + " from URI: " + nextUri);

                try (Response nextResponse = gitHubRestClient.searchRepositoriesByUri(
                        nextUri, "alns-rcpharm-ghrepos-scorer", authHeader)) {

                    pageCount++;
                    GitHubSearchResponseDto nextDto = nextResponse.readEntity(GitHubSearchResponseDto.class);
                    if (nextDto != null && nextDto.getItems() != null) {
                        nextDto.getItems().stream().map(this::mapToDomain).forEach(accumulated::add);
                    }

                    linkHeader = nextResponse.getHeaderString("Link");
                    nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);
                }
            }
        } catch (Exception e) {
            log.warn("GitHub API request for query '" + query + "' returned error: " + e.getMessage() + ". Returning accumulated items.");
        }

        return accumulated;
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
