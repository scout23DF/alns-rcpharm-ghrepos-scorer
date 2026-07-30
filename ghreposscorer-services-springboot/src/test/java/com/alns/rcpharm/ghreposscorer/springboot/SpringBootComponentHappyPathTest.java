package com.alns.rcpharm.ghreposscorer.springboot;

import com.alns.rcpharm.ghreposscorer.springboot.adapter.in.rest.GitHubScoreController;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringBootComponentHappyPathTest {

    private static final WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @Autowired
    private GitHubScoreController gitHubScoreController;

    private MockMvc mockMvc;

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        mockMvc = MockMvcBuilders.standaloneSetup(gitHubScoreController).build();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("github.api.url", wireMockServer::baseUrl);
    }

    @Test
    @DisplayName("Happy Path: GET /api/v1/repositories/popular should fetch, score, and sort GitHub repositories")
    void testGetPopularRepositoriesSuccess() throws Exception {
        String page1Json = """
                {
                  "total_count": 1,
                  "items": [
                    {
                      "id": 101,
                      "name": "spring-boot",
                      "full_name": "spring-projects/spring-boot",
                      "html_url": "https://github.com/spring-projects/spring-boot",
                      "description": "Spring Boot Framework",
                      "language": "Java",
                      "stargazers_count": 70000,
                      "forks_count": 40000,
                      "pushed_at": "2026-07-29T10:00:00Z"
                    }
                  ]
                }
                """;

        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(page1Json)
                        .withStatus(200)));

        mockMvc.perform(get("/api/v1/repositories/popular")
                        .param("language", "Java")
                        .param("created_after", "2015-01-01")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Happy Path: GET /api/v1/repositories/popular/stream should stream SSE events")
    void testGetPopularRepositoriesStreamSuccess() throws Exception {
        String page1Json = """
                {
                  "total_count": 1,
                  "items": [
                    {
                      "id": 105,
                      "name": "spring-framework",
                      "full_name": "spring-projects/spring-framework",
                      "html_url": "https://github.com/spring-projects/spring-framework",
                      "description": "Spring Framework Core",
                      "language": "Java",
                      "stargazers_count": 55000,
                      "forks_count": 35000,
                      "pushed_at": "2026-07-30T10:00:00Z"
                    }
                  ]
                }
                """;

        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(page1Json)
                        .withStatus(200)));

        mockMvc.perform(get("/api/v1/repositories/popular/stream")
                        .param("language", "Java")
                        .param("created_after", "2015-01-01")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/event-stream")));
    }

    @Test
    @DisplayName("Happy Path: RFC 5988 Pagination should follow rel='next' Link header")
    void testPaginationWithLinkHeader() throws Exception {
        String page2Url = wireMockServer.baseUrl() + "/search/repositories?q=language%3AJava+created%3A%3E2015-01-01&sort=stars&order=desc&per_page=100&page=2";

        String page1Json = """
                {
                  "total_count": 2,
                  "items": [
                    {
                      "id": 101,
                      "name": "repo-page-1",
                      "full_name": "org/repo-page-1",
                      "html_url": "https://github.com/org/repo-page-1",
                      "language": "Java",
                      "stargazers_count": 5000,
                      "forks_count": 1000,
                      "pushed_at": "2026-07-29T10:00:00Z"
                    }
                  ]
                }
                """;

        String page2Json = """
                {
                  "total_count": 2,
                  "items": [
                    {
                      "id": 102,
                      "name": "repo-page-2",
                      "full_name": "org/repo-page-2",
                      "html_url": "https://github.com/org/repo-page-2",
                      "language": "Java",
                      "stargazers_count": 3000,
                      "forks_count": 500,
                      "pushed_at": "2026-07-28T10:00:00Z"
                    }
                  ]
                }
                """;

        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/search/repositories"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link", "<" + page2Url + ">; rel=\"next\"")
                        .withBody(page1Json)
                        .withStatus(200)));

        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/search/repositories"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(page2Json)
                        .withStatus(200)));

        mockMvc.perform(get("/api/v1/repositories/popular")
                        .param("language", "Java")
                        .param("created_after", "2015-01-01")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Happy Path: GET & PUT /api/v1/config/scoring should retrieve and update scoring parameters")
    void testGetAndUpdateScoringConfig() throws Exception {
        mockMvc.perform(get("/api/v1/config/scoring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wStars", notNullValue()));

        String newConfigJson = """
                {
                  "wStars": 2.0,
                  "wForks": 1.5,
                  "wRecency": 1.0,
                  "decayLambda": 0.02,
                  "defaultCreatedAfter": "2010-01-01",
                  "defaultPopularityLimit": 30,
                  "shouldHandleGHApiPagination": false,
                  "maxPagesToFetch": 3,
                  "delayBetweenGHApiRequestsMillis": 0
                }
                """;

        mockMvc.perform(put("/api/v1/config/scoring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newConfigJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wStars", is(2.0)));
    }
}
