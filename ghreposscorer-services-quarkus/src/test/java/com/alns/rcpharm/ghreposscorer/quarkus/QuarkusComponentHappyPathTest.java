package com.alns.rcpharm.ghreposscorer.quarkus;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(GitHubApiWireMockTestResource.class)
class QuarkusComponentHappyPathTest {

    @BeforeEach
    void resetWireMock() {
        if (GitHubApiWireMockTestResource.wireMockServer != null) {
            GitHubApiWireMockTestResource.wireMockServer.resetAll();
        }
    }

    @Test
    @DisplayName("Happy Path Quarkus: GET /api/v1/repositories/popular should fetch, score, and sort GitHub repositories")
    void testGetPopularRepositoriesSuccess() {
        String page1Json = """
                {
                  "total_count": 1,
                  "items": [
                    {
                      "id": 201,
                      "name": "quarkus-core",
                      "full_name": "quarkusio/quarkus",
                      "html_url": "https://github.com/quarkusio/quarkus",
                      "description": "Supersonic Subatomic Java",
                      "language": "Java",
                      "stargazers_count": 25000,
                      "forks_count": 4000,
                      "pushed_at": "2026-07-30T10:00:00Z"
                    }
                  ]
                }
                """;

        GitHubApiWireMockTestResource.wireMockServer.stubFor(get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(page1Json)
                        .withStatus(200)));

        given()
                .queryParam("language", "Java")
                .queryParam("created_after", "2015-01-01")
                .queryParam("limit", 5)
                .when()
                .get("/api/v1/repositories/popular")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Happy Path Quarkus: GET /api/v1/repositories/popular/stream should stream SSE events")
    void testGetPopularRepositoriesStreamSuccess() {
        String page1Json = """
                {
                  "total_count": 1,
                  "items": [
                    {
                      "id": 301,
                      "name": "mutiny-reactive",
                      "full_name": "smallrye/mutiny",
                      "html_url": "https://github.com/smallrye/mutiny",
                      "description": "Intuitive Event-Driven Reactive Programming",
                      "language": "Java",
                      "stargazers_count": 15000,
                      "forks_count": 2000,
                      "pushed_at": "2026-07-30T10:00:00Z"
                    }
                  ]
                }
                """;

        GitHubApiWireMockTestResource.wireMockServer.stubFor(get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(page1Json)
                        .withStatus(200)));

        given()
                .queryParam("language", "Java")
                .queryParam("created_after", "2015-01-01")
                .queryParam("limit", 5)
                .when()
                .get("/api/v1/repositories/popular/stream")
                .then()
                .statusCode(200)
                .contentType(containsString("text/event-stream"));
    }

    @Test
    @DisplayName("Happy Path Quarkus: GET & PUT /api/v1/config/scoring should retrieve and update config")
    void testGetAndUpdateScoringConfig() {
        given()
                .when()
                .get("/api/v1/config/scoring")
                .then()
                .statusCode(200)
                .body("wStars", notNullValue());

        Map<String, Object> newConfig = Map.of(
                "wStars", 1.8,
                "wForks", 1.4,
                "wRecency", 1.0,
                "decayLambda", 0.015,
                "defaultCreatedAfter", "2010-01-01",
                "defaultPopularityLimit", 30,
                "shouldHandleGHApiPagination", false,
                "maxPagesToFetch", 3,
                "delayBetweenGHApiRequestsMillis", 0L
        );

        given()
                .contentType(ContentType.JSON)
                .body(newConfig)
                .when()
                .put("/api/v1/config/scoring")
                .then()
                .statusCode(200)
                .body("wStars", equalTo(1.8F));
    }
}
