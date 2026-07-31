package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubSearchResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.util.function.Supplier;

public class RetryAttemptsUtils {

    private static final Logger log = LoggerFactory.getLogger(RetryAttemptsUtils.class);

    public static ResponseEntity<GitHubSearchResponseDto> fetchResponseWithRetry(
            ScoreConfig scoreConfig,
            Supplier<ResponseEntity<GitHubSearchResponseDto>> requestSupplier
    ) {
        int maxRetries = scoreConfig != null && scoreConfig.maxRetriesAttempts() != null ? scoreConfig.maxRetriesAttempts() : 3;
        long delayMs = (scoreConfig != null && scoreConfig.delayBetweenGHApiRequestsMillis() != null && scoreConfig.delayBetweenGHApiRequestsMillis() > 0)
                ? scoreConfig.delayBetweenGHApiRequestsMillis() : 1000L;
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


}
