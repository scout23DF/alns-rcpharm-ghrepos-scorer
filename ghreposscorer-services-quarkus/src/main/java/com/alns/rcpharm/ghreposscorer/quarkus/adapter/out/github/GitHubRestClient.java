package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github;

import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.net.URI;

@RegisterRestClient(configKey = "github-api")
public interface GitHubRestClient {

    @GET
    @Path("/search/repositories")
    @Produces(MediaType.APPLICATION_JSON)
    Response searchRepositories(
            @QueryParam("q") String query,
            @QueryParam("sort") String sort,
            @QueryParam("order") String order,
            @QueryParam("per_page") int perPage,
            @HeaderParam("User-Agent") String userAgent,
            @HeaderParam("Authorization") String authorization
    );

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Response searchRepositoriesByUri(
            @Url URI url,
            @HeaderParam("User-Agent") String userAgent,
            @HeaderParam("Authorization") String authorization
    );
}
