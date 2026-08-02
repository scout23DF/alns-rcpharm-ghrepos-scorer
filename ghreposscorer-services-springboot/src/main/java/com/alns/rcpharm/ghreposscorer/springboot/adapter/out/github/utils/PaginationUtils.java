package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.utils.GitHubLinkHeaderParser;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.GitHubFeignClient;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubSearchResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class PaginationUtils {

    private static final Logger log = LoggerFactory.getLogger(PaginationUtils.class);

    public static void fetchGHRepositoriesPaginated(
            GitHubFeignClient gitHubFeignClient,
            ScoreConfig scoreConfig,
            String language,
            LocalDate createdAfter,
            Consumer<List<GitHubRepository>> pageConsumer,
            BooleanSupplier isCancelled) {

        String query = String.format("language:%s created:>%s", language, createdAfter.toString());
        String token = System.getenv("GITHUB_TOKEN");
        String authHeader = (token != null && !token.isBlank()) ? "Bearer " + token : null;

        int maxPages = scoreConfig != null && scoreConfig.maxPagesToFetch() != null ? scoreConfig.maxPagesToFetch() : 5;
        Long delay = scoreConfig != null ? scoreConfig.delayBetweenGHApiRequestsMillis() : null;

        log.info("Fetching GitHub repositories for query: {}", query);
        int pageCount = 0;

        try {

            ResponseEntity<GitHubSearchResponseDto> responseFirstPage = handleFirstPageFetching(
                    gitHubFeignClient,
                    scoreConfig,
                    query,
                    authHeader,
                    pageConsumer,
                    isCancelled
            );

            if (responseFirstPage != null) {
                handleRemainingPagesFetching(
                        responseFirstPage,
                        gitHubFeignClient,
                        scoreConfig,
                        authHeader,
                        pageConsumer,
                        isCancelled
                );
            }
        } catch (Exception e) {
            log.warn("Exception during GitHub pagination in Spring Boot: {}. Gracefully delivering accumulated items fetched so far.", e.getMessage());
        }
    }

    private static ResponseEntity<GitHubSearchResponseDto> handleFirstPageFetching(
            GitHubFeignClient gitHubFeignClient,
            ScoreConfig scoreConfig,
            String query,
            String authHeader,
            Consumer<List<GitHubRepository>> pageConsumer,
            BooleanSupplier isCancelled
    ) {

        ResponseEntity<GitHubSearchResponseDto> responseFirstPage = RetryAttemptsUtils.fetchResponseWithRetry(
                scoreConfig,
                () -> gitHubFeignClient.searchRepositories(
                        query,
                        "stars",
                        "desc",
                        100,
                        1,
                        "alns-rcpharm-ghrepos-scorer-springboot",
                        authHeader
                )
        );

        if (responseFirstPage == null || responseFirstPage.getBody() == null || responseFirstPage.getStatusCode().value() != 200) {
            log.warn("GitHub API initial page request failed or returned non-200 status ({}). Delivering accumulated items fetched so far.", responseFirstPage != null ? responseFirstPage.getStatusCode().value() : "null");
            return null;
        }

        handleResponseMapping(pageConsumer, isCancelled, responseFirstPage);

        return responseFirstPage;
    }

    private static void handleRemainingPagesFetching(
            ResponseEntity<GitHubSearchResponseDto> responseFirstPage,
            GitHubFeignClient gitHubFeignClient,
            ScoreConfig scoreConfig,
            String authHeader,
            Consumer<List<GitHubRepository>> pageConsumer,
            BooleanSupplier isCancelled
    ) {
        boolean handlePagination = scoreConfig != null ? Boolean.TRUE.equals(scoreConfig.shouldHandleGHApiPagination()) : true;
        int maxPages = scoreConfig != null && scoreConfig.maxPagesToFetch() != null ? scoreConfig.maxPagesToFetch() : 5;
        Long delay = scoreConfig != null ? scoreConfig.delayBetweenGHApiRequestsMillis() : null;
        int pageCount = 1;

        String linkHeader = responseFirstPage.getHeaders().getFirst("link");
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

            ResponseEntity<GitHubSearchResponseDto> nextResponse = RetryAttemptsUtils.fetchResponseWithRetry(
                    scoreConfig,
                    () -> gitHubFeignClient.searchRepositoriesByUri(
                            nextUri,
                            "alns-rcpharm-ghrepos-scorer-springboot",
                            authHeader
                    )
            );

            if (nextResponse == null || nextResponse.getStatusCode().value() != 200) {
                log.warn("GitHub API page {} request failed or returned non-200 status ({}). Gracefully ending pagination and delivering accumulated items.", pageCount + 1, nextResponse != null ? nextResponse.getStatusCode().value() : "null");
                break;
            }

            pageCount++;

            handleResponseMapping(pageConsumer, isCancelled, nextResponse);

            linkHeader = nextResponse.getHeaders().getFirst("link");
            nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);

        } // while

    }

    private static void handleResponseMapping(Consumer<List<GitHubRepository>> pageConsumer,
                                              BooleanSupplier isCancelled,
                                              ResponseEntity<GitHubSearchResponseDto> responseEntity) {

        GitHubSearchResponseDto dto = responseEntity.getBody();
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
