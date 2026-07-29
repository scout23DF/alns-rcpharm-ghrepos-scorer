package com.alns.rcpharm.ghreposscorer.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain record representing a GitHub repository.
 */
public record Repository(
    String id,
    String name,
    String fullName,
    String htmlUrl,
    String description,
    String language,
    long stars,
    long forks,
    Instant pushedAt
) {
    public Repository {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
    }
}
