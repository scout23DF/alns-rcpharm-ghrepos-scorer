package com.alns.rcpharm.ghreposscorer.quarkus;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

public class GitHubApiWireMockTestResource implements QuarkusTestResourceLifecycleManager {

    public static WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        return Map.of(
                "quarkus.rest-client.\"github-api\".url", wireMockServer.baseUrl(),
                "quarkus.rest-client.github-api.url", wireMockServer.baseUrl()
        );
    }

    @Override
    public void stop() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }
}
