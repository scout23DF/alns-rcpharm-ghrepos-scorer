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

        try {
            Response response = fetchResponseWithRetry(() -> gitHubRestClient.searchRepositories(
                    query, "stars", "desc", 100, "alns-rcpharm-ghrepos-scorer-quarkus", authHeader));

            if (response == null || response.getStatus() != 200) {
                log.warn("GitHub API initial page request failed or returned non-200 status (" + (response != null ? response.getStatus() : "null") + "). Delivering accumulated items fetched so far.");
                return;
            }

            pageCount++;
            handleResponseMapping(pageConsumer, isCancelled, response);

            String linkHeader = response.getHeaderString("link");
            if (linkHeader == null) {
                linkHeader = response.getHeaderString("Link");
            }
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

                Response nextResponse = fetchResponseWithRetry(() -> gitHubRestClient.searchRepositoriesByUri(
                        nextUri, "alns-rcpharm-ghrepos-scorer-quarkus", authHeader));

                if (nextResponse == null || nextResponse.getStatus() != 200) {
                    log.warn("GitHub API page " + (pageCount + 1) + " request failed or returned non-200 status (" + (nextResponse != null ? nextResponse.getStatus() : "null") + "). Gracefully ending pagination and delivering accumulated items.");
                    break;
                }

                pageCount++;
                handleResponseMapping(pageConsumer, isCancelled, nextResponse);

                linkHeader = nextResponse.getHeaderString("link");
                if (linkHeader == null) {
                    linkHeader = nextResponse.getHeaderString("Link");
                }
                nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);
            }
        } catch (Exception e) {
            log.warn("Exception during GitHub pagination: " + e.getMessage() + ". Gracefully delivering accumulated items fetched so far.");
        }
    }

    private static Response fetchResponseWithRetry(java.util.function.Supplier<Response> requestSupplier) {
        int maxRetries = 3;
        long delayMs = 1000;
        Response response = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                response = requestSupplier.get();
                if (response != null) {
                    int status = response.getStatus();
                    if (status == 200) {
                        return response;
                    }
                    if (status == 429 || status == 403 || status >= 500) {
                        log.warn(String.format("GitHub API call returned status %d. Attempt %d/%d failed. Retrying in %d ms...",
                                status, attempt, maxRetries, delayMs));
                        if (attempt < maxRetries) {
                            try {
                                Thread.sleep(delayMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                            delayMs *= 2;
                            continue;
                        }
                    }
                }
                return response;
            } catch (Exception e) {
                log.warn(String.format("GitHub API call failed (%s). Attempt %d/%d failed. Retrying in %d ms...",
                        e.getMessage(), attempt, maxRetries, delayMs));
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    delayMs *= 2;
                } else {
                    log.error("GitHub API request failed after max retries (" + maxRetries + "): " + e.getMessage());
                }
            }
        }
        return response;
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
