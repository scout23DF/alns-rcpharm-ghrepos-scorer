package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.util.GitHubLinkHeaderParser;
import com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.GitHubRestClient;
import com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.dto.GitHubSearchResponseDto;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class PaginationUtils {

    private static final Logger log = Logger.getLogger(PaginationUtils.class);

    public static void fetchGHRepositoriesPaginated(
            GitHubRestClient gitHubRestClient,
            ScoreConfig scoreConfig,
            String language,
            LocalDate createdAfter,
            Consumer<List<GitHubRepository>> pageConsumer,
            BooleanSupplier isCancelled) {

        String query = String.format("language:%s created:>%s", language, createdAfter.toString());
        String token = System.getenv("GITHUB_TOKEN");
        String authHeader = (token != null && !token.isBlank()) ? "Bearer " + token : null;

        boolean handlePagination = scoreConfig != null ? Boolean.TRUE.equals(scoreConfig.shouldHandleGHApiPagination()) : true;
        int maxPages = scoreConfig != null && scoreConfig.maxPagesToFetch() != null ? scoreConfig.maxPagesToFetch() : 5;
        Long delay = scoreConfig != null ? scoreConfig.delayBetweenGHApiRequestsMillis() : null;

        log.info("Fetching GitHub repositories for query: " + query);
        int pageCount = 0;

        try (Response response = gitHubRestClient.searchRepositories(
                query, "stars", "desc", 100, "alns-rcpharm-ghrepos-scorer-quarkus", authHeader)) {

            if (response.getStatus() == 429 || response.getStatus() == 403) {
                throw new com.alns.rcpharm.ghreposscorer.domain.exception.GitHubRateLimitException("GitHub API Rate Limit / Access Limit exceeded (" + response.getStatus() + ")");
            }

            pageCount++;
            if (response.getStatus() == 200) {
                handleResponseMapping(pageConsumer, isCancelled, response);

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
                            handleResponseMapping(pageConsumer, isCancelled, nextResponse);
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

    private static void handleResponseMapping(Consumer<List<GitHubRepository>> pageConsumer,
                                              BooleanSupplier isCancelled,
                                              Response response) {
        GitHubSearchResponseDto dto = response.readEntity(GitHubSearchResponseDto.class);
        if (dto != null && dto.getItems() != null) {
            List<GitHubRepository> pageItems = dto.getItems().stream()
                    .map(MapperUtils::mapDtoToDomain)
                    .toList();
            if (!pageItems.isEmpty() && !isCancelled.getAsBoolean()) {
                pageConsumer.accept(pageItems);
            }
        }
    }

}
