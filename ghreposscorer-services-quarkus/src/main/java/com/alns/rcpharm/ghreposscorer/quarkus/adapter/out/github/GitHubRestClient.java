package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github;

import com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.dto.GitHubSearchResponseDto;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/search/repositories")
@RegisterRestClient(configKey = "github-api")
public interface GitHubRestClient {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    GitHubSearchResponseDto searchRepositories(
            @QueryParam("q") String query,
            @QueryParam("sort") String sort,
            @QueryParam("order") String order,
            @QueryParam("per_page") int perPage,
            @HeaderParam("User-Agent") String userAgent,
            @HeaderParam("Authorization") String authorization
    );
}
