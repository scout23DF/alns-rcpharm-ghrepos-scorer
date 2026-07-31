package com.alns.rcpharm.ghreposscorer.cli;

import com.alns.rcpharm.ghreposscorer.domain.model.PopularityScore;
import com.alns.rcpharm.ghreposscorer.domain.port.in.ListScoredGHReposRankingUseCase;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

import io.quarkus.picocli.runtime.annotations.TopCommand;

/**
 * PicoCLI command for fetching and displaying GitHub repository popularity scores.
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

    @Option(names = {"-l", "--language"}, description = "Programming language to search (e.g. Java, Kotlin, Python)", defaultValue = "Java")
    String language;

    @Option(names = {"-d", "--created-after"}, description = "Filter repositories created after date (YYYY-MM-DD)", defaultValue = "2010-01-01")
    String createdAfterDate;

    @Option(names = {"-n", "--limit"}, description = "Maximum number of repositories to display", defaultValue = "10")
    int limit;

    @Override
    public Integer call() {
        LocalDate createdAfter = LocalDate.parse(createdAfterDate, DateTimeFormatter.ISO_LOCAL_DATE);
        System.out.printf("Fetching popular repositories for language '%s' created after %s (Limit: %d)...%n%n",
                language, createdAfter, limit);

        List<PopularityScore> scores = listScoredGHReposRankingUseCase.getPopularRepositories(language, createdAfter, limit);

        if (scores.isEmpty()) {
            System.out.println("No repositories found matching the given criteria.");
            return 0;
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

        return 0;
    }
}
