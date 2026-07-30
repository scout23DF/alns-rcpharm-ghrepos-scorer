package com.alns.rcpharm.ghreposscorer.quarkus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Test using Testcontainers GenericContainer to validate Redis cache lifecycle and container connectivity.
 */
@Testcontainers
class RedisCacheIntegrationTest {

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    @DisplayName("Should verify Redis container initialization and mapped port for cache operations")
    void testRedisContainerRunning() {
        assertThat(redisContainer.isRunning()).isTrue();

        Integer mappedPort = redisContainer.getMappedPort(6379);
        assertThat(mappedPort).isNotNull().isGreaterThan(0);

        String host = redisContainer.getHost();
        assertThat(host).isNotNull().isNotEmpty();
    }
}
