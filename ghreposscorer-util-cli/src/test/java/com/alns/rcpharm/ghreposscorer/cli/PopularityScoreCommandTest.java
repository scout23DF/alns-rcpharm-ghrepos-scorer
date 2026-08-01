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
    @DisplayName("CLI Test: Should execute PopularityScoreCommand synchronously with arguments")
    void testPopularityScoreCommandSynchronousExecution() {
        CommandLine commandLine = new CommandLine(popularityScoreCommand);
        int exitCode = commandLine.execute("-l", "Kotlin", "-n", "3");

        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("CLI Test: Should execute PopularityScoreCommand reactively with -s / --stream flag")
    void testPopularityScoreCommandReactiveStreamExecution() {
        CommandLine commandLine = new CommandLine(popularityScoreCommand);
        int exitCode = commandLine.execute("-l", "Kotlin", "-n", "3", "-s");

        assertThat(exitCode).isEqualTo(0);
    }
}
