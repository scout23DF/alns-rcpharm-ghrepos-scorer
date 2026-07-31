package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.util.GitHubLinkHeaderParser;
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

        boolean handlePagination = scoreConfig != null ? Boolean.TRUE.equals(scoreConfig.shouldHandleGHApiPagination()) : true;
        int maxPages = scoreConfig != null && scoreConfig.maxPagesToFetch() != null ? scoreConfig.maxPagesToFetch() : 5;
        Long delay = scoreConfig != null ? scoreConfig.delayBetweenGHApiRequestsMillis() : null;

        log.info("Fetching GitHub repositories for query: {}", query);
        int pageCount = 0;

        try {
            ResponseEntity<GitHubSearchResponseDto> responseEntity = fetchResponseWithRetry(
                    () -> gitHubFeignClient.searchRepositories(query, "stars", "desc", 100, 1, "alns-rcpharm-ghrepos-scorer-springboot", authHeader));

            if (responseEntity == null || responseEntity.getBody() == null) {
                log.warn("GitHub API initial page request failed or returned empty response. Delivering accumulated items fetched so far.");
                return;
            }

            pageCount++;
            handleResponseMapping(pageConsumer, isCancelled, responseEntity);

            String linkHeader = responseEntity.getHeaders().getFirst("link");
            if (linkHeader == null) {
                linkHeader = responseEntity.getHeaders().getFirst("Link");
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

                log.info("Fetching next page {} dynamically for query: {}", pageCount + 1, query);

                final int currentPageToFetch = pageCount + 1;
                ResponseEntity<GitHubSearchResponseDto> nextResponse = fetchResponseWithRetry(
                        () -> gitHubFeignClient.searchRepositories(query, "stars", "desc", 100, currentPageToFetch, "alns-rcpharm-ghrepos-scorer-springboot", authHeader));

                if (nextResponse == null || nextResponse.getBody() == null) {
                    log.warn("GitHub API page {} request failed or returned empty response. Gracefully ending pagination and delivering accumulated items.", currentPageToFetch);
                    break;
                }

                pageCount++;
                handleResponseMapping(pageConsumer, isCancelled, nextResponse);

                linkHeader = nextResponse.getHeaders().getFirst("link");
                if (linkHeader == null) {
                    linkHeader = nextResponse.getHeaders().getFirst("Link");
                }
                nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);
            }
        } catch (Exception e) {
            log.warn("Exception during GitHub pagination in Spring Boot: {}. Gracefully delivering accumulated items fetched so far.", e.getMessage());
        }
    }

    private static ResponseEntity<GitHubSearchResponseDto> fetchResponseWithRetry(java.util.function.Supplier<ResponseEntity<GitHubSearchResponseDto>> requestSupplier) {
        int maxRetries = 3;
        long delayMs = 1000;
        ResponseEntity<GitHubSearchResponseDto> response = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                response = requestSupplier.get();
                if (response != null && response.getStatusCode().is2xxSuccessful()) {
                    return response;
                }
                if (response != null && (response.getStatusCode().value() == 429 || response.getStatusCode().value() == 403 || response.getStatusCode().is5xxServerError())) {
                    log.warn("GitHub API call returned status {}. Attempt {}/{} failed. Retrying in {} ms...",
                            response.getStatusCode().value(), attempt, maxRetries, delayMs);
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
                return response;
            } catch (Exception e) {
                log.warn("GitHub API call failed ({}). Attempt {}/{} failed. Retrying in {} ms...",
                        e.getMessage(), attempt, maxRetries, delayMs);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    delayMs *= 2;
                } else {
                    log.error("GitHub API request failed after max retries ({}): {}", maxRetries, e.getMessage());
                }
            }
        }
        return response;
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
