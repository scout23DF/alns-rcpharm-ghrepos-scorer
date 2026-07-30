package com.alns.rcpharm.ghreposscorer.springboot.adapter.in.rest;

import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.CalculatePopularityUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "GitHub Repository Popularity Scorer", description = "Endpoints for computing popularity scores and managing scoring config")
public class GitHubScoreController {

    private final CalculatePopularityUseCase calculatePopularityUseCase;
    private final UpdateScoreConfigUseCase updateScoreConfigUseCase;
    private final CacheManager cacheManager;

    public GitHubScoreController(CalculatePopularityUseCase calculatePopularityUseCase,
                                 UpdateScoreConfigUseCase updateScoreConfigUseCase,
                                 CacheManager cacheManager) {
        this.calculatePopularityUseCase = calculatePopularityUseCase;
        this.updateScoreConfigUseCase = updateScoreConfigUseCase;
        this.cacheManager = cacheManager;
    }

    @GetMapping({"/repositories/popular", "/github-repositories/popular"})
    @Operation(summary = "Get popular GitHub repositories scored and sorted")
    public ResponseEntity<List<PopularityScore>> getPopularRepositories(
            @RequestParam("language") String language,
            @RequestParam("created_after") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAfter,
            @RequestParam(value = "limit", defaultValue = "30") int limit) {

        List<PopularityScore> scores = calculatePopularityUseCase.getPopularRepositories(language, createdAfter, limit);
        return ResponseEntity.ok(scores);
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
