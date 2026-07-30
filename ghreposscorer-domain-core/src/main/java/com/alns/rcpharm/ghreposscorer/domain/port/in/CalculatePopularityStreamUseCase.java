package com.alns.rcpharm.ghreposscorer.domain.port.in;

import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;

import java.time.LocalDate;
import java.util.concurrent.Flow;

/**
 * Reactive input port interface for streaming GitHub repository popularity scores.
 */
public interface CalculatePopularityStreamUseCase {

    /**
     * Retrieves a reactive stream of popular GitHub repositories calculated according to scoring configuration.
     *
     * @param language     Programming language
     * @param createdAfter Earliest created date
     * @param limit        Maximum number of repositories to stream
     * @return Flow.Publisher emitting PopularityScore items
     */
    Flow.Publisher<PopularityScore> getPopularRepositoriesStream(String language, LocalDate createdAfter, int limit);
}
