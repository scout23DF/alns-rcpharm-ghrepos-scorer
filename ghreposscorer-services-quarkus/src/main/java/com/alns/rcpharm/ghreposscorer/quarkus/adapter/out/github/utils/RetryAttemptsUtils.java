package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.utils;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.function.Supplier;

public class RetryAttemptsUtils {

    private static final Logger log = Logger.getLogger(RetryAttemptsUtils.class);

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

}
