package com.alns.rcpharm.ghreposscorer.springboot.adapter.in.rest;

import com.alns.rcpharm.ghreposscorer.domain.exception.GitHubRateLimitException;
import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.ListScoredGHReposRankingStreamUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.ListScoredGHReposRankingUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cache.CacheManager;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.reactivestreams.FlowAdapters;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Flow;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "GitHub Repository Popularity Scorer", description = "Endpoints for computing popularity scores and managing scoring config")
public class GitHubScoreController {

    private final ListScoredGHReposRankingUseCase listScoredGHReposRankingUseCase;
    private final ListScoredGHReposRankingStreamUseCase listScoredGHReposRankingStreamUseCase;
    private final UpdateScoreConfigUseCase updateScoreConfigUseCase;
    private final CacheManager cacheManager;

    public GitHubScoreController(ListScoredGHReposRankingUseCase listScoredGHReposRankingUseCase,
                                 ListScoredGHReposRankingStreamUseCase listScoredGHReposRankingStreamUseCase,
                                 UpdateScoreConfigUseCase updateScoreConfigUseCase,
                                 CacheManager cacheManager) {
        this.listScoredGHReposRankingUseCase = listScoredGHReposRankingUseCase;
        this.listScoredGHReposRankingStreamUseCase = listScoredGHReposRankingStreamUseCase;
        this.updateScoreConfigUseCase = updateScoreConfigUseCase;
        this.cacheManager = cacheManager;
    }

    @GetMapping({"/repositories/popular", "/github-repositories/popular"})
    @Operation(summary = "Get popular GitHub repositories scored and sorted")
    public ResponseEntity<List<PopularityScore>> getPopularRepositories(
            @RequestParam("language") String language,
            @RequestParam("created_after") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAfter,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {

        List<PopularityScore> scores = listScoredGHReposRankingUseCase.getPopularRepositories(language, createdAfter, limit);
        return ResponseEntity.ok(scores);
    }

    @GetMapping(value = "/repositories/popular/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream popular GitHub repositories as Server-Sent Events (SSE)")
    public Flux<List<PopularityScore>> getPopularRepositoriesStream(
            @RequestParam("language") String language,
            @RequestParam("created_after") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAfter,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {

        Flow.Publisher<List<PopularityScore>> publisher = listScoredGHReposRankingStreamUseCase.getPopularRepositoriesStream(language, createdAfter, limit);

        return Flux.from(FlowAdapters.toPublisher(publisher))
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(10))
                        .filter(throwable -> throwable instanceof GitHubRateLimitException));
    }

    @GetMapping("/config/scoring")
    @Operation(summary = "Get current scoring configuration weights and decay factor")
    public ResponseEntity<ScoreConfig> getCurrentConfig() {
        return ResponseEntity.ok(updateScoreConfigUseCase.getCurrentConfig());
    }

    @PutMapping("/config/scoring")
    @Operation(summary = "Update scoring configuration and invalidate repository score cache")
    public ResponseEntity<ScoreConfig> updateConfig(@RequestBody ScoreConfig newConfig) {
        ScoreConfig updated = updateScoreConfigUseCase.updateConfig(newConfig);
        return ResponseEntity.ok(updated);
    }
}
