package com.alns.rcpharm.ghreposscorer.quarkus.adapter.in.rest;

import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;
import com.alns.rcpharm.ghreposscorer.domain.port.in.CalculatePopularityUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.UpdateScoreConfigUseCase;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "GitHub Repository Popularity Scorer", description = "Endpoints for computing popularity scores and managing scoring config")
public class GitHubScoreResource {

    @Inject
    CalculatePopularityUseCase calculatePopularityUseCase;

    @Inject
    UpdateScoreConfigUseCase updateScoreConfigUseCase;

    @GET
    @Path("/repositories/popular")
    @Operation(summary = "Get popular GitHub repositories scored and sorted")
    public Response getPopularRepositories(
            @QueryParam("language") String language,
            @QueryParam("created_after") String createdAfterStr,
            @DefaultValue("30") @QueryParam("limit") int limit) {

        if (language == null || language.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("language is required").build();
        }
        if (createdAfterStr == null || createdAfterStr.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("created_after is required").build();
        }

        LocalDate createdAfter = LocalDate.parse(createdAfterStr);
        List<PopularityScore> scores = calculatePopularityUseCase.getPopularRepositories(language, createdAfter, limit);
        return Response.ok(scores).build();
    }

    @GET
    @Path("/github-repositories/popular")
    @Operation(summary = "Get popular GitHub repositories scored and sorted (alias)")
    public Response getPopularGitHubRepositories(
            @QueryParam("language") String language,
            @QueryParam("created_after") String createdAfterStr,
            @DefaultValue("30") @QueryParam("limit") int limit) {

        return getPopularRepositories(language, createdAfterStr, limit);
    }

    @GET
    @Path("/config/scoring")
    @Operation(summary = "Get current scoring configuration weights and decay factor")
    public Response getCurrentConfig() {
        return Response.ok(updateScoreConfigUseCase.getCurrentConfig()).build();
    }

    @PUT
    @Path("/config/scoring")
    @CacheInvalidateAll(cacheName = "github-repositories")
    @Operation(summary = "Update scoring configuration and invalidate repository score cache")
    public Response updateConfig(ScoreConfig newConfig) {
        if (newConfig == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("ScoreConfig body is required").build();
        }
        ScoreConfig updated = updateScoreConfigUseCase.updateConfig(newConfig);
        return Response.ok(updated).build();
    }
}
