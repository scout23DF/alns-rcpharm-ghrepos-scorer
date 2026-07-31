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

        ResponseEntity<com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubSearchResponseDto> responseEntity = gitHubFeignClient.searchRepositories(
                query, "stars", "desc", 100, 1, "alns-rcpharm-ghrepos-scorer-springboot", authHeader
        );
        pageCount++;

        if (responseEntity.getBody() != null && responseEntity.getBody().getItems() != null) {
            handleResponseMapping(pageConsumer, isCancelled, responseEntity);
        }

        String linkHeader = responseEntity.getHeaders().getFirst("link");
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

            ResponseEntity<com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubSearchResponseDto> nextResponse = gitHubFeignClient.searchRepositories(
                    query, "stars", "desc", 100, pageCount + 1, "alns-rcpharm-ghrepos-scorer-springboot", authHeader
            );
            pageCount++;

            if (nextResponse.getBody() != null && nextResponse.getBody().getItems() != null) {
                handleResponseMapping(pageConsumer, isCancelled, nextResponse);
            }

            linkHeader = nextResponse.getHeaders().getFirst("link");
            if (linkHeader == null) {
                linkHeader = nextResponse.getHeaders().getFirst("Link");
            }
            nextUriOpt = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);
        }

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
