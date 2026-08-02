package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubSearchResponseDto;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
        RetryIterationHolderDTO retryIterationHolderDTO = new RetryIterationHolderDTO();

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
                retryIterationHolderDTO.logLevel = LogLevel.INFO;
                retryIterationHolderDTO.currentAttemptsCount = attemptsCount;

                response = requestSupplier.get();

                retryIterationHolderDTO.lastResponseReturned = response;

            } catch (Exception ex) {
                retryIterationHolderDTO.lastResponseReturned = response;
                retryIterationHolderDTO.occurredException = ex;
            } // try

            handleRetryAttemptDelay(retryIterationHolderDTO, maxRetries);

            switch (retryIterationHolderDTO.logLevel) {
                case WARN:
                    log.warn(retryIterationHolderDTO.logMessage);
                    break;
                case ERROR:
                    log.error(retryIterationHolderDTO.logMessage);
                    break;
                default:
                    log.info(retryIterationHolderDTO.logMessage);
                    break;
            }

            if (retryIterationHolderDTO.mustBreakLoop) {
                break;
            }

            if (retryIterationHolderDTO.mustContinueLoop) {
                continue;
            }

        } // for

        return response;
    }

    private static void handleRetryAttemptDelay(RetryIterationHolderDTO retryIterationHolderDTO, int maxRetries) {

        if (retryIterationHolderDTO.getHttpStatusCode() == HttpStatus.OK.value()) {

            long calculatedDelay = parseRateLimitDelayMs(
                    retryIterationHolderDTO.hasOccurredException(),
                    retryIterationHolderDTO.getHttpStatusCode(),
                    retryIterationHolderDTO.getResponseHeadersAsMap(),
                    retryIterationHolderDTO.defaultDelayMs,
                    retryIterationHolderDTO.currentAttemptsCount
            );

            retryIterationHolderDTO.logLevel = LogLevel.INFO;
            retryIterationHolderDTO.logMessage = String.format(
                    "**** SUCCESSFULLY GitHub API call returned status %s. Attempt %d/%d succeeded. ****",
                    retryIterationHolderDTO.getHttpStatusCode(), retryIterationHolderDTO.currentAttemptsCount, maxRetries);

            try {
                Thread.sleep(calculatedDelay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            retryIterationHolderDTO.mustBreakLoop = true;

        } else {
            long calculatedDelay = parseRateLimitDelayMs(
                    retryIterationHolderDTO.hasOccurredException(),
                    retryIterationHolderDTO.getHttpStatusCode(),
                    retryIterationHolderDTO.getResponseHeadersAsMap(),
                    retryIterationHolderDTO.defaultDelayMs,
                    retryIterationHolderDTO.currentAttemptsCount
            );

            retryIterationHolderDTO.logLevel = LogLevel.WARN;

            if (!retryIterationHolderDTO.hasOccurredException()) {
                retryIterationHolderDTO.logMessage = String.format(
                        "GitHub API call returned status %d. Attempt %d/%d failed. Retrying in %d ms...",
                        retryIterationHolderDTO.getHttpStatusCode(), retryIterationHolderDTO.currentAttemptsCount, maxRetries, calculatedDelay);
            } else {
                retryIterationHolderDTO.logMessage = String.format(
                        "GitHub API call via FeignClient failed (%s). Attempt %d/%d failed. Retrying in %d ms...",
                        retryIterationHolderDTO.occurredException.getMessage(), retryIterationHolderDTO.currentAttemptsCount, maxRetries, calculatedDelay
                );
            }

            if (retryIterationHolderDTO.currentAttemptsCount < maxRetries) {

                try {
                    Thread.sleep(calculatedDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    retryIterationHolderDTO.mustBreakLoop = true;
                }

                if (!retryIterationHolderDTO.mustBreakLoop) {
                    retryIterationHolderDTO.defaultDelayMs *= 2;
                    retryIterationHolderDTO.mustContinueLoop = true;
                }

            } else {
                retryIterationHolderDTO.logLevel = LogLevel.ERROR;
                retryIterationHolderDTO.logMessage = String.format(
                        "GitHub API request failed after max retries (%d): %s",
                        maxRetries, retryIterationHolderDTO.occurredException.getMessage()
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


    private static class RetryIterationHolderDTO {
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
