package com.alns.rcpharm.ghreposscorer.cli;

import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.port.in.ListScoredGHReposRankingStreamUseCase;
import com.alns.rcpharm.ghreposscorer.domain.port.in.ListScoredGHReposRankingUseCase;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/**
 * PicoCLI command for fetching and displaying GitHub repository popularity scores.
 * Supports synchronous execution, reactive stream mode, and interactive REPL session mode.
 */
@TopCommand
@Command(
    name = "github-score",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Calculates GitHub repository popularity scores for a specified programming language."
)
public class PopularityScoreCommand implements Callable<Integer> {

    @Inject
    ListScoredGHReposRankingUseCase listScoredGHReposRankingUseCase;

    @Inject
    ListScoredGHReposRankingStreamUseCase listScoredGHReposRankingStreamUseCase;

    @Option(names = {"-l", "--language"}, description = "Programming language to search (e.g. Java, Kotlin, Python)", defaultValue = "Java")
    String language;

    @Option(names = {"-d", "--created-after"}, description = "Filter repositories created after date (YYYY-MM-DD)", defaultValue = "2010-01-01")
    String createdAfterDate;

    @Option(names = {"-n", "--limit"}, description = "Maximum number of repositories to display", defaultValue = "10")
    int limit;

    @Option(names = {"-s", "--stream"}, description = "Fetch repositories reactively via reactive stream (Flow.Publisher / SSE)")
    boolean reactiveStream;

    @Option(names = {"-i", "--interactive"}, description = "Run CLI in open interactive REPL session mode until '/q' or '/quit'")
    boolean interactive;

    public PopularityScoreCommand() {}

    public PopularityScoreCommand(ListScoredGHReposRankingUseCase listScoredGHReposRankingUseCase,
                                  ListScoredGHReposRankingStreamUseCase listScoredGHReposRankingStreamUseCase) {
        this.listScoredGHReposRankingUseCase = listScoredGHReposRankingUseCase;
        this.listScoredGHReposRankingStreamUseCase = listScoredGHReposRankingStreamUseCase;
    }

    @Override
    public Integer call() {
        executeSearch();

        if (interactive) {
            runInteractiveLoop();
        }

        return 0;
    }

    private void executeSearch() {
        LocalDate createdAfter = LocalDate.parse(createdAfterDate, DateTimeFormatter.ISO_LOCAL_DATE);
        System.out.printf("Fetching popular repositories for language '%s' created after %s (Limit: %d, Mode: %s)...%n%n",
                language, createdAfter, limit, reactiveStream ? "REACTIVE STREAM" : "SYNCHRONOUS");

        List<PopularityScore> scores;
        if (reactiveStream) {
            scores = fetchScoresFromStream(language, createdAfter, limit);
        } else {
            scores = listScoredGHReposRankingUseCase.getPopularRepositories(language, createdAfter, limit);
        }

        if (scores.isEmpty()) {
            System.out.println("No repositories found matching the given criteria.");
            return;
        }

        System.out.println("==========================================================================================================");
        System.out.printf(" %-4s | %-12s | %-10s | %-10s | %-12s | %-40s%n",
                "RANK", "SCORE", "STARS", "FORKS", "LAST PUSHED", "REPOSITORY NAME");
        System.out.println("----------------------------------------------------------------------------------------------------------");

        int rank = 1;
        for (PopularityScore score : scores) {
            String pushed = score.repository().pushedAt() != null ?
                    score.repository().pushedAt().toString().substring(0, 10) : "N/A";
            System.out.printf(" %-4d | %-12.2f | %-10d | %-10d | %-12s | %-40s%n",
                    rank++,
                    score.score(),
                    score.repository().stars(),
                    score.repository().forks(),
                    pushed,
                    score.repository().fullName());
        }
        System.out.println("==========================================================================================================");
    }

    private void runInteractiveLoop() {
        System.out.println("\nInteractive session started. Type options (e.g. -l Kotlin -n 5 -s) or '/q' / '/quit' to exit.");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\nghreposscorer> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("/q") || input.equalsIgnoreCase("/quit")
                    || input.equalsIgnoreCase("q") || input.equalsIgnoreCase("quit")
                    || input.equalsIgnoreCase("exit")) {
                System.out.println("Exiting GitHub Repositories Scorer CLI session. Goodbye!");
                break;
            }
            if (input.isBlank()) {
                continue;
            }

            String[] args = input.split("\\s+");
            PopularityScoreCommand newCmd = new PopularityScoreCommand(
                    this.listScoredGHReposRankingUseCase,
                    this.listScoredGHReposRankingStreamUseCase
            );
            CommandLine commandLine = new CommandLine(newCmd);
            commandLine.execute(args);
        }
    }

    private List<PopularityScore> fetchScoresFromStream(String language, LocalDate createdAfter, int limit) {
        List<PopularityScore> accumulatedScores = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Flow.Publisher<List<PopularityScore>> publisher =
                listScoredGHReposRankingStreamUseCase.getPopularRepositoriesStream(language, createdAfter, limit);

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(List<PopularityScore> items) {
                accumulatedScores.addAll(items);
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println("Error in reactive stream: " + throwable.getMessage());
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return accumulatedScores;
    }
}
