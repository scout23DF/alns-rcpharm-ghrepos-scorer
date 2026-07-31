package com.alns.rcpharm.ghreposscorer.domain.port.in;

import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * Reactive input port interface for streaming GitHub repository popularity scores.
 */
public interface ListScoredGHReposRankingStreamUseCase {

    Flow.Publisher<List<PopularityScore>> getPopularRepositoriesStream(String language, LocalDate createdAfter, int limit);
}
