package com.alns.rcpharm.ghreposscorer.springboot;

import com.alns.rcpharm.ghreposscorer.springboot.adapter.in.rest.GitHubScoreController;
import com.alns.rcpharm.ghreposscorer.springboot.adapter.in.rest.GlobalExceptionHandler;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringBootComponentNegativeTest {

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
        mockMvc = MockMvcBuilders.standaloneSetup(gitHubScoreController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("github.api.url", wireMockServer::baseUrl);
    }

    @Test
    @DisplayName("Negative Path: Missing required language parameter should return 400 Bad Request with RFC 7807 ProblemDetail")
    void testMissingLanguageParameter() throws Exception {
        mockMvc.perform(get("/api/v1/repositories/popular")
                        .param("created_after", "2015-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", notNullValue()));
    }

    @Test
    @DisplayName("Negative Path: GitHub API 500 Internal Error should trigger Resilience4j Circuit Breaker fallback")
    void testCircuitBreakerFallbackOnGitHubError() throws Exception {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"message\": \"Internal Server Error\"}")));

        mockMvc.perform(get("/api/v1/repositories/popular")
                        .param("language", "Java")
                        .param("created_after", "2015-01-01")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Negative Path: GitHub Rate Limit 429 response should trigger fallback gracefully")
    void testGitHubRateLimitExceededFallback() throws Exception {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/search/repositories"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "60")
                        .withBody("{\"message\": \"API rate limit exceeded\"}")));

        mockMvc.perform(get("/api/v1/repositories/popular")
                        .param("language", "Python")
                        .param("created_after", "2015-01-01")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
