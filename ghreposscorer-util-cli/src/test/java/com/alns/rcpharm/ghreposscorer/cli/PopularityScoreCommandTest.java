package com.alns.rcpharm.ghreposscorer.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PopularityScoreCommandTest {

    @Inject
    @TopCommand
    PopularityScoreCommand popularityScoreCommand;

    @Test
    @DisplayName("CLI Test: Should execute PopularityScoreCommand successfully with arguments")
    void testPopularityScoreCommandExecution() {
        CommandLine commandLine = new CommandLine(popularityScoreCommand);
        int exitCode = commandLine.execute("-l", "Kotlin", "-n", "3");

        assertThat(exitCode).isEqualTo(0);
    }
}
