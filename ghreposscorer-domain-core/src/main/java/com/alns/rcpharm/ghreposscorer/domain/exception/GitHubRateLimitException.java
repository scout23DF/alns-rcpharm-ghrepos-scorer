package com.alns.rcpharm.ghreposscorer.domain.exception;

/**
 * Domain exception thrown when GitHub API rate limits or secondary throttling occur.
 */
public class GitHubRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public GitHubRateLimitException(String message) {
        super(message);
        this.retryAfterSeconds = 60;
    }

    public GitHubRateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
