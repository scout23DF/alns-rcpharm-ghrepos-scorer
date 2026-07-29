package com.alns.rcpharm.ghreposscorer.domain.port.out;

import com.alns.rcpharm.ghreposscorer.domain.model.Repository;
import java.time.LocalDate;
import java.util.List;

/**
 * Output Port interface for retrieving repository data from GitHub.
 */
public interface GitHubRepositoryPort {
    List<Repository> fetchRepositories(String language, LocalDate createdAfter);
}
