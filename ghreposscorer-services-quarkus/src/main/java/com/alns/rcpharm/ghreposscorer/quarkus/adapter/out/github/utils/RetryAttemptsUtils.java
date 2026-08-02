package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.function.Supplier;

public class RetryAttemptsUtils {

    private static final Logger log = Logger.getLogger(RetryAttemptsUtils.class);
    private static final long MAX_RETRY_DELAY_MS = 5000L;

    public static Response fetchResponseWithRetry(ScoreConfig scoreConfig, Supplier<Response> requestSupplier) {
        int maxRetries = scoreConfig != null && scoreConfig.maxRetriesAttempts() != null ? scoreConfig.maxRetriesAttempts() : 3;
        long delayMs = (scoreConfig != null && scoreConfig.delayBetweenGHApiRequestsMillis() != null && scoreConfig.delayBetweenGHApiRequestsMillis() > 0)
                ? scoreConfig.delayBetweenGHApiRequestsMillis() : 1000L;
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
                        long calculatedDelay = parseRateLimitDelayMs(status, response, delayMs);
                        log.warn(String.format("GitHub API call returned status %d. Attempt %d/%d failed. Retrying in %d ms...",
                                status, attempt, maxRetries, calculatedDelay));
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
                }
                return response;
            } catch (Exception e) {
                long calculatedDelay = delayMs;
                if (e instanceof WebApplicationException wae && wae.getResponse() != null) {
                    Response errResp = wae.getResponse();
                    calculatedDelay = parseRateLimitDelayMs(errResp.getStatus(), errResp, delayMs);
                }
                log.warn(String.format("GitHub API call failed (%s). Attempt %d/%d failed. Retrying in %d ms...",
                        e.getMessage(), attempt, maxRetries, calculatedDelay));
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(calculatedDelay);
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

    private static long parseRateLimitDelayMs(int statusCode, Response response, long defaultDelayMs) {
        if ((statusCode == 403 || statusCode == 429) && response != null) {
            String retryAfter = response.getHeaderString("retry-after");
            if (retryAfter == null) retryAfter = response.getHeaderString("Retry-After");

            if (retryAfter != null && !retryAfter.isBlank()) {
                try {
                    long retrySec = Long.parseLong(retryAfter.trim());
                    if (retrySec > 0) {
                        log.info("GitHub API 'retry-after' header detected: waiting " + retrySec + " seconds.");
                        return Math.min(retrySec * 1000L, MAX_RETRY_DELAY_MS);
                    }
                } catch (NumberFormatException ignored) {}
            }

            String reset = response.getHeaderString("x-ratelimit-reset");
            if (reset == null) reset = response.getHeaderString("X-RateLimit-Reset");

            if (reset != null && !reset.isBlank()) {
                try {
                    long resetEpochSec = Long.parseLong(reset.trim());
                    long currentEpochSec = System.currentTimeMillis() / 1000L;
                    long waitSec = resetEpochSec - currentEpochSec;

                    if (waitSec > 0) {
                        log.info("GitHub API 'x-ratelimit-reset' header detected: waiting " + waitSec + " seconds until reset.");
                        return Math.min(waitSec * 1000L, MAX_RETRY_DELAY_MS);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return Math.min(defaultDelayMs, MAX_RETRY_DELAY_MS);
    }
}
