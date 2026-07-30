package com.alns.rcpharm.ghreposscorer.quarkus.adapter.in.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.Map;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        int status = (exception instanceof IllegalArgumentException) ? 400 : 500;
        String title = (status == 400) ? "Bad Request" : "Internal Server Error";

        Map<String, Object> errorBody = Map.of(
                "title", title,
                "status", status,
                "detail", exception.getMessage() != null ? exception.getMessage() : "An unexpected error occurred",
                "timestamp", Instant.now().toString()
        );

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(errorBody)
                .build();
    }
}
