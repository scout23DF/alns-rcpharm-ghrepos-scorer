package com.alns.rcpharm.ghreposscorer.quarkus;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(GitHubApiWireMockTestResource.class)
class QuarkusComponentNegativeTest {

    @BeforeEach
    void resetWireMock() {
        if (GitHubApiWireMockTestResource.wireMockServer != null) {
            GitHubApiWireMockTestResource.wireMockServer.resetAll();
        }
    }

    @Test
    @DisplayName("Negative Path Quarkus: Missing language parameter should return 400 Bad Request")
    void testMissingLanguageParameter() {
        given()
                .queryParam("created_after", "2015-01-01")
                .when()
                .get("/api/v1/repositories/popular")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Negative Path Quarkus: GitHub API 500 Error should trigger SmallRye Fault Tolerance fallback")
    void testSmallRyeFaultToleranceFallbackOnServerError() {
        GitHubApiWireMockTestResource.wireMockServer.stubFor(get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"message\": \"Internal Server Error\"}")));

        given()
                .queryParam("language", "Rust")
                .queryParam("created_after", "2020-01-01")
                .queryParam("limit", 5)
                .when()
                .get("/api/v1/repositories/popular")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    @DisplayName("Negative Path Quarkus: GitHub Rate Limit 429 Error should trigger fallback gracefully")
    void testGitHubRateLimitExceededFallback() {
        GitHubApiWireMockTestResource.wireMockServer.stubFor(get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "60")
                        .withBody("{\"message\": \"API rate limit exceeded\"}")));

        given()
                .queryParam("language", "Elixir")
                .queryParam("created_after", "2020-01-01")
                .queryParam("limit", 5)
                .when()
                .get("/api/v1/repositories/popular")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }
}
