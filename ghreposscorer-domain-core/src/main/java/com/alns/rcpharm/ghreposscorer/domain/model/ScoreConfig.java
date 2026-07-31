package com.alns.rcpharm.ghreposscorer.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Domain record holding weight parameters, decay factor, popular languages,
 * and default createdAfter date for the popularity scoring algorithm.
 */
public record ScoreConfig(
    double wStars,
    double wForks,
    double wRecency,
    double decayLambda,
    List<String> popularLanguages,
    LocalDate defaultCreatedAfter,
    Long delayBetweenGHApiRequestsMillis,
    Integer defaultPopularityLimit,
    Boolean shouldHandleGHApiPagination,
    Integer maxPagesToFetch,
    Integer maxRetriesAttempts
) {
    public static final List<String> DEFAULT_POPULAR_LANGUAGES = List.of("Java", "Python", "TypeScript");
    public static final LocalDate DEFAULT_CREATED_AFTER = LocalDate.of(2010, 1, 1);
    public static final Long DEFAULT_DELAY_BETWEEN_GHAPI_REQUESTS = 1000L;
    public static final Integer DEFAULT_POPULARITY_LIMIT = 30;
    public static final Boolean DEFAULT_SHOULD_HANDLE_GHAPI_PAGINATION = true;
    public static final Integer DEFAULT_MAX_PAGES_TO_FETCH = 3;
    public static final Integer DEFAULT_MAX_RETRIES_ATTEMPTS = 3;

    public ScoreConfig {
        if (popularLanguages == null) {
            popularLanguages = DEFAULT_POPULAR_LANGUAGES;
        }
        if (defaultCreatedAfter == null) {
            defaultCreatedAfter = DEFAULT_CREATED_AFTER;
        }
        if (delayBetweenGHApiRequestsMillis == null) {
            delayBetweenGHApiRequestsMillis = DEFAULT_DELAY_BETWEEN_GHAPI_REQUESTS;
        }
        if (defaultPopularityLimit == null) {
            defaultPopularityLimit = DEFAULT_POPULARITY_LIMIT;
        }
        if (shouldHandleGHApiPagination == null) {
            shouldHandleGHApiPagination = DEFAULT_SHOULD_HANDLE_GHAPI_PAGINATION;
        }
        if (maxPagesToFetch == null) {
            maxPagesToFetch = DEFAULT_MAX_PAGES_TO_FETCH;
        }
        if (maxRetriesAttempts == null) {
            maxRetriesAttempts = DEFAULT_MAX_RETRIES_ATTEMPTS;
        }
    }

    public ScoreConfig(double wStars, double wForks, double wRecency, double decayLambda) {
        this(wStars,
            wForks,
            wRecency,
            decayLambda,
            DEFAULT_POPULAR_LANGUAGES,
            DEFAULT_CREATED_AFTER,
            DEFAULT_DELAY_BETWEEN_GHAPI_REQUESTS,
            DEFAULT_POPULARITY_LIMIT,
            DEFAULT_SHOULD_HANDLE_GHAPI_PAGINATION,
            DEFAULT_MAX_PAGES_TO_FETCH,
            DEFAULT_MAX_RETRIES_ATTEMPTS
        );
    }

    public ScoreConfig(double wStars, double wForks, double wRecency, double decayLambda, List<String> popularLanguages, LocalDate defaultCreatedAfter) {
        this(wStars,
            wForks,
            wRecency,
            decayLambda,
            popularLanguages,
            defaultCreatedAfter,
            DEFAULT_DELAY_BETWEEN_GHAPI_REQUESTS,
            DEFAULT_POPULARITY_LIMIT,
            DEFAULT_SHOULD_HANDLE_GHAPI_PAGINATION,
            DEFAULT_MAX_PAGES_TO_FETCH,
            DEFAULT_MAX_RETRIES_ATTEMPTS
        );
    }

    public ScoreConfig(double wStars, double wForks, double wRecency, double decayLambda, List<String> popularLanguages, LocalDate defaultCreatedAfter, Long delayBetweenGHApiRequestsMillis) {
        this(wStars,
            wForks,
            wRecency,
            decayLambda,
            popularLanguages,
            defaultCreatedAfter,
            delayBetweenGHApiRequestsMillis,
            DEFAULT_POPULARITY_LIMIT,
            DEFAULT_SHOULD_HANDLE_GHAPI_PAGINATION,
            DEFAULT_MAX_PAGES_TO_FETCH,
            DEFAULT_MAX_RETRIES_ATTEMPTS
        );
    }

    public static ScoreConfig defaultConfig() {
        return new ScoreConfig(
                1.0,
                1.2,
                0.8,
                0.01,
                DEFAULT_POPULAR_LANGUAGES,
                DEFAULT_CREATED_AFTER,
                DEFAULT_DELAY_BETWEEN_GHAPI_REQUESTS,
                DEFAULT_POPULARITY_LIMIT,
                DEFAULT_SHOULD_HANDLE_GHAPI_PAGINATION,
                DEFAULT_MAX_PAGES_TO_FETCH,
                DEFAULT_MAX_RETRIES_ATTEMPTS
        );
    }

}

