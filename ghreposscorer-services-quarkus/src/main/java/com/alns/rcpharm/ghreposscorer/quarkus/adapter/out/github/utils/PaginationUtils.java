package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.utils.GitHubLinkHeaderParser;
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

        log.info("Fetching GitHub repositories for query: " + query);

        try {

            Response responseFirstPage = handleFirstPageFetching(
                    gitHubRestClient,
                    scoreConfig,
                    query,
                    authHeader,
                    pageConsumer,
                    isCancelled
            );

            if (responseFirstPage != null) {
                handleRemainingPagesFetching(
                        responseFirstPage,
                        gitHubRestClient,
                        scoreConfig,
                        authHeader,
                        pageConsumer,
                        isCancelled
                );
            }

        } catch (Exception e) {
            log.warn("Exception during GitHub pagination: " + e.getMessage() + ". Gracefully delivering accumulated items fetched so far.");
        }
    }

    private static Response handleFirstPageFetching(
            GitHubRestClient gitHubRestClient,
            ScoreConfig scoreConfig,
            String query,
            String authHeader,
            Consumer<List<GitHubRepository>> pageConsumer,
            BooleanSupplier isCancelled
    ) {

        Response responseFirstPage = RetryAttemptsUtils.fetchResponseWithRetry(
                scoreConfig,
                () -> gitHubRestClient.searchRepositories(
                        query,
                        "stars",
                        "desc",
                        100,
                        "alns-rcpharm-ghrepos-scorer-quarkus",
                        authHeader
                )
        );

        if (responseFirstPage == null || responseFirstPage.getStatus() != 200) {
            log.warn("GitHub API initial page request failed or returned non-200 status (" + (responseFirstPage != null ? responseFirstPage.getStatus() : "null") + "). Delivering accumulated items fetched so far.");
            return null;
        }

        handleResponseMapping(pageConsumer, isCancelled, responseFirstPage);

        return responseFirstPage;
    }

    private static void handleRemainingPagesFetching(
            Response responseFirstPage,
            GitHubRestClient gitHubRestClient,
            ScoreConfig scoreConfig,
            String authHeader,
            Consumer<List<GitHubRepository>> pageConsumer,
            BooleanSupplier isCancelled
    ) {
        boolean handlePagination = scoreConfig != null ? Boolean.TRUE.equals(scoreConfig.shouldHandleGHApiPagination()) : true;
        int maxPages = scoreConfig != null && scoreConfig.maxPagesToFetch() != null ? scoreConfig.maxPagesToFetch() : 5;
        Long delay = scoreConfig != null ? scoreConfig.delayBetweenGHApiRequestsMillis() : null;
        int pageCount = 1;

        String linkHeader = responseFirstPage.getHeaderString("link");
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

            Response nextResponse = RetryAttemptsUtils.fetchResponseWithRetry(
                    scoreConfig,
                    () -> gitHubRestClient.searchRepositoriesByUri(
                            nextUri,
                            "alns-rcpharm-ghrepos-scorer-quarkus",
                            authHeader
                    )
            );

            if (nextResponse == null || nextResponse.getStatus() != 200) {
                log.warn("GitHub API page " + (pageCount + 1) + " request failed or returned non-200 status (" + (nextResponse != null ? nextResponse.getStatus() : "null") + "). Gracefully ending pagination and delivering accumulated items.");
                break;
            }

            pageCount++;

            handleResponseMapping(pageConsumer, isCancelled, nextResponse);

            linkHeader = nextResponse.getHeaderString("link");
            nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);

        } // while

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
