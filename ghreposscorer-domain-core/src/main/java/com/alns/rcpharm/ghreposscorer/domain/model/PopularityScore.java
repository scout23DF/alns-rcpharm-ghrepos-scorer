package com.alns.rcpharm.ghreposscorer.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain record encapsulating a Repository along with its calculated popularity score and calculation timestamp.
 */
public record PopularityScore(
    Repository repository,
    double score,
    Instant calculatedAt
) {
    public PopularityScore {
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(calculatedAt, "calculatedAt must not be null");
    }
}
