package com.alns.rcpharm.ghreposscorer.domain.port.in;

import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import java.time.LocalDate;
import java.util.List;

/**
 * Input Port interface for fetching and calculating repository popularity scores.
 */
public interface ListScoredGHReposRankingUseCase {
    List<PopularityScore> getPopularRepositories(String language, LocalDate createdAfter, int limit);
}
