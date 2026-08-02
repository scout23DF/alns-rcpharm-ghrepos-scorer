package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubSearchResponseDto;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

public class RetryAttemptsUtils {

    private static final Logger log = LoggerFactory.getLogger(RetryAttemptsUtils.class);
    private static final long MAX_RETRY_DELAY_MS = 5000L;

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
                    long calculatedDelay = parseRateLimitDelayMs(response.getStatusCode().value(), response.getHeaders(), delayMs);
                    log.warn("GitHub API call returned status {}. Attempt {}/{} failed. Retrying in {} ms...",
                            response.getStatusCode().value(), attempt, maxRetries, calculatedDelay);
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(calculatedDelay);
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
                long calculatedDelay = delayMs;
                if (e instanceof FeignException feignEx) {
                    calculatedDelay = parseFeignRateLimitDelayMs(feignEx.status(), feignEx.responseHeaders(), delayMs);
                }
                log.warn("GitHub API call failed ({}). Attempt {}/{} failed. Retrying in {} ms...",
                        e.getMessage(), attempt, maxRetries, calculatedDelay);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(calculatedDelay);
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

    private static long parseRateLimitDelayMs(int statusCode, HttpHeaders headers, long defaultDelayMs) {
        if ((statusCode == 403 || statusCode == 429) && headers != null) {
            String retryAfter = headers.getFirst("retry-after");
            if (retryAfter == null) retryAfter = headers.getFirst("Retry-After");

            if (retryAfter != null && !retryAfter.isBlank()) {
                try {
                    long retrySec = Long.parseLong(retryAfter.trim());
                    if (retrySec > 0) {
                        log.info("GitHub API 'retry-after' header detected: waiting {} seconds.", retrySec);
                        return Math.min(retrySec * 1000L, MAX_RETRY_DELAY_MS);
                    }
                } catch (NumberFormatException ignored) {}
            }

            String reset = headers.getFirst("x-ratelimit-reset");
            if (reset == null) reset = headers.getFirst("X-RateLimit-Reset");

            if (reset != null && !reset.isBlank()) {
                try {
                    long resetEpochSec = Long.parseLong(reset.trim());
                    long currentEpochSec = System.currentTimeMillis() / 1000L;
                    long waitSec = resetEpochSec - currentEpochSec;

                    if (waitSec > 0) {
                        log.info("GitHub API 'x-ratelimit-reset' header detected: waiting {} seconds until reset.", waitSec);
                        return Math.min(waitSec * 1000L, MAX_RETRY_DELAY_MS);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return Math.min(defaultDelayMs, MAX_RETRY_DELAY_MS);
    }

    private static long parseFeignRateLimitDelayMs(int statusCode, Map<String, Collection<String>> headers, long defaultDelayMs) {
        if ((statusCode == 403 || statusCode == 429) && headers != null) {
            String retryAfter = getHeaderValue(headers, "retry-after", "Retry-After");
            if (retryAfter != null && !retryAfter.isBlank()) {
                try {
                    long retrySec = Long.parseLong(retryAfter.trim());
                    if (retrySec > 0) {
                        log.info("GitHub API 'retry-after' header detected from Feign: waiting {} seconds.", retrySec);
                        return Math.min(retrySec * 1000L, MAX_RETRY_DELAY_MS);
                    }
                } catch (NumberFormatException ignored) {}
            }

            String reset = getHeaderValue(headers, "x-ratelimit-reset", "X-RateLimit-Reset");
            if (reset != null && !reset.isBlank()) {
                try {
                    long resetEpochSec = Long.parseLong(reset.trim());
                    long currentEpochSec = System.currentTimeMillis() / 1000L;
                    long waitSec = resetEpochSec - currentEpochSec;

                    if (waitSec > 0) {
                        log.info("GitHub API 'x-ratelimit-reset' header detected from Feign: waiting {} seconds until reset.", waitSec);
                        return Math.min(waitSec * 1000L, MAX_RETRY_DELAY_MS);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return Math.min(defaultDelayMs, MAX_RETRY_DELAY_MS);
    }

    private static String getHeaderValue(Map<String, Collection<String>> headers, String... keys) {
        if (headers == null) return null;
        for (String key : keys) {
            Collection<String> values = headers.get(key);
            if (values != null && !values.isEmpty()) {
                return values.iterator().next();
            }
        }
        return null;
    }
}
