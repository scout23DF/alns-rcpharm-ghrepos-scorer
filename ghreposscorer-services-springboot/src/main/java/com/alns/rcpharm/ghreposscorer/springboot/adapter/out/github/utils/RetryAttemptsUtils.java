package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubSearchResponseDto;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RetryAttemptsUtils {

    private static final Logger log = LoggerFactory.getLogger(RetryAttemptsUtils.class);
    private static final long MAX_RETRY_DELAY_MS = 5000L;

    public static ResponseEntity<GitHubSearchResponseDto> fetchResponseWithRetry(
            ScoreConfig scoreConfig,
            Supplier<ResponseEntity<GitHubSearchResponseDto>> requestSupplier
    ) {
        ResponseEntity<GitHubSearchResponseDto> response = null;
        RetryIteractionHolderDTO retryIteractionHolderDTO = new RetryIteractionHolderDTO();

        int maxRetries = (
                (scoreConfig != null && scoreConfig.maxRetriesAttempts() != null && scoreConfig.maxRetriesAttempts() > 0)
                ? scoreConfig.maxRetriesAttempts()
                : 3
        );
        long delayMs = (
                (scoreConfig != null && scoreConfig != null && scoreConfig.delayBetweenGHApiRequestsMillis() != null && scoreConfig.delayBetweenGHApiRequestsMillis() > 0)
                ? scoreConfig.delayBetweenGHApiRequestsMillis()
                : 1000L
        );

        for (int attemptsCount = 1; attemptsCount <= maxRetries; attemptsCount++) {

            try {
                retryIteractionHolderDTO.logLevel = LogLevel.INFO;
                retryIteractionHolderDTO.currentAttemptsCount = attemptsCount;

                response = requestSupplier.get();

                retryIteractionHolderDTO.lastResponseReturned = response;

            } catch (Exception ex) {
                retryIteractionHolderDTO.lastResponseReturned = response;
                retryIteractionHolderDTO.occurredException = ex;
            } // try

            handleRetryAttemptDelay(retryIteractionHolderDTO, maxRetries);

            switch (retryIteractionHolderDTO.logLevel) {
                case WARN:
                    log.warn(retryIteractionHolderDTO.logMessage);
                    break;
                case ERROR:
                    log.error(retryIteractionHolderDTO.logMessage);
                    break;
                default:
                    log.info(retryIteractionHolderDTO.logMessage);
                    break;
            }

            if (retryIteractionHolderDTO.mustBreakLoop) {
                break;
            }

            if (retryIteractionHolderDTO.mustContinueLoop) {
                continue;
            }

        } // for

        return response;
    }

    private static void handleRetryAttemptDelay(RetryIteractionHolderDTO retryIteractionHolderDTO, int maxRetries) {

        if (retryIteractionHolderDTO.getHttpStatusCode() == HttpStatus.OK.value()) {

            long calculatedDelay = parseRateLimitDelayMs(
                    retryIteractionHolderDTO.hasOccurredException(),
                    retryIteractionHolderDTO.getHttpStatusCode(),
                    retryIteractionHolderDTO.getResponseHeadersAsMap(),
                    retryIteractionHolderDTO.defaultDelayMs,
                    retryIteractionHolderDTO.currentAttemptsCount
            );

            retryIteractionHolderDTO.logLevel = LogLevel.INFO;
            retryIteractionHolderDTO.logMessage = String.format(
                    "**** SUCCESSFULLY GitHub API call returned status %s. Attempt %d/%d succeeded. ****",
                    retryIteractionHolderDTO.getHttpStatusCode(), retryIteractionHolderDTO.currentAttemptsCount, maxRetries);

            try {
                Thread.sleep(calculatedDelay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            retryIteractionHolderDTO.mustBreakLoop = true;

        } else {
            long calculatedDelay = parseRateLimitDelayMs(
                    retryIteractionHolderDTO.hasOccurredException(),
                    retryIteractionHolderDTO.getHttpStatusCode(),
                    retryIteractionHolderDTO.getResponseHeadersAsMap(),
                    retryIteractionHolderDTO.defaultDelayMs,
                    retryIteractionHolderDTO.currentAttemptsCount
            );

            retryIteractionHolderDTO.logLevel = LogLevel.WARN;

            if (!retryIteractionHolderDTO.hasOccurredException()) {
                retryIteractionHolderDTO.logMessage = String.format(
                        "GitHub API call returned status %d. Attempt %d/%d failed. Retrying in %d ms...",
                        retryIteractionHolderDTO.getHttpStatusCode(), retryIteractionHolderDTO.currentAttemptsCount, maxRetries, calculatedDelay);
            } else {
                retryIteractionHolderDTO.logMessage = String.format(
                        "GitHub API call via FeignClient failed (%s). Attempt %d/%d failed. Retrying in %d ms...",
                        retryIteractionHolderDTO.occurredException.getMessage(), retryIteractionHolderDTO.currentAttemptsCount, maxRetries, calculatedDelay
                );
            }

            if (retryIteractionHolderDTO.currentAttemptsCount < maxRetries) {

                try {
                    Thread.sleep(calculatedDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    retryIteractionHolderDTO.mustBreakLoop = true;
                }

                if (!retryIteractionHolderDTO.mustBreakLoop) {
                    retryIteractionHolderDTO.defaultDelayMs *= 2;
                    retryIteractionHolderDTO.mustContinueLoop = true;
                }

            } else {
                retryIteractionHolderDTO.logLevel = LogLevel.ERROR;
                retryIteractionHolderDTO.logMessage = String.format(
                        "GitHub API request failed after max retries (%d): %s",
                        maxRetries, retryIteractionHolderDTO.occurredException.getMessage()
                );
            }

        }

    }

    private static long parseRateLimitDelayMs(boolean hasOccurredException,
                                              int statusCode,
                                              Map<String, String> repsonseHeadersMap,
                                              long defaultDelayMs,
                                              int attemptsCount) {


        if (repsonseHeadersMap != null) {
            String retryAfter = getHeaderValue(repsonseHeadersMap, "retry-after", "Retry-After");

            if (retryAfter != null && !retryAfter.isBlank()) {
                try {
                    long retrySec = Long.parseLong(retryAfter.trim());
                    if (retrySec > 0) {
                        if (statusCode >= 400 && statusCode <= 503) {
                            if (!hasOccurredException) {
                                log.info("GitHub API 'retry-after' header detected: waiting {} seconds.", retrySec);
                            } else {
                                log.info("GitHub API 'retry-after' header detected from FeignClient Exception: waiting {} seconds.", retrySec);
                            }
                        }
                        return (Math.min(retrySec * 1000L, MAX_RETRY_DELAY_MS)) * attemptsCount;
                    }
                } catch (NumberFormatException ignored) {

                }
            }

            String reset = getHeaderValue(repsonseHeadersMap, "x-ratelimit-reset", "X-RateLimit-Reset");

            if (reset != null && !reset.isBlank()) {
                try {
                    long resetEpochSec = Long.parseLong(reset.trim());
                    long currentEpochSec = System.currentTimeMillis() / 1000L;
                    long waitSec = resetEpochSec - currentEpochSec;

                    if (waitSec > 0) {
                        if (statusCode >= 400 && statusCode <= 503) {
                            if (!hasOccurredException) {
                                log.info("GitHub API 'x-ratelimit-reset' header detected: waiting {} seconds until reset.", waitSec);
                            } else {
                                log.info("GitHub API 'x-ratelimit-reset' header detected from FeignClient Exception: waiting {} seconds until reset.", waitSec);
                            }
                        }
                        return (Math.min(waitSec * 1000L, MAX_RETRY_DELAY_MS)) * attemptsCount;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return Math.min(defaultDelayMs, MAX_RETRY_DELAY_MS) * attemptsCount;

    }

    private static String getHeaderValue(Map<String, String> headers, String... keys) {

        if (headers == null) {
            return null;
        }

        for (String key : keys) {
            if (headers.containsKey(key)) {
                return headers.get(key);
            }
        }
        return null;
    }


    private static class RetryIteractionHolderDTO {
        ResponseEntity<GitHubSearchResponseDto> lastResponseReturned = null;
        Exception occurredException = null;
        LogLevel logLevel = LogLevel.WARN;
        String logMessage = null;
        long defaultDelayMs;
        int currentAttemptsCount;
        boolean mustBreakLoop = false;
        boolean mustContinueLoop = false;

        public boolean hasOccurredException() {
            return (occurredException != null);
        }

        public int getHttpStatusCode() {
            if (hasOccurredException()) {
                return (occurredException instanceof FeignException feignEx) ? feignEx.status() : 503;
            } else {
                return (lastResponseReturned != null) ? lastResponseReturned.getStatusCode().value() : 503;
            }
        }

        public Map<String, String> getResponseHeadersAsMap() {
            HttpHeaders headersFromResponse;
            Map<String, Collection<String>> headersFromFeignException;

            if (!hasOccurredException() && lastResponseReturned != null) {
                return lastResponseReturned.getHeaders().toSingleValueMap();
            }

            if (hasOccurredException() && occurredException instanceof FeignException feignEx) {
                Map<String, String> tempMap = new HashMap<>();
                feignEx.responseHeaders().forEach((key, values) -> {
                    if (values != null && !values.isEmpty()) {
                        tempMap.put(key, values.iterator().next());
                    } else {
                        tempMap.put(key, null);
                    }
                });
                return tempMap;
            }

            return Map.of();
        }

    }

}
