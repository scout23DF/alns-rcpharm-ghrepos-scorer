package com.alns.rcpharm.ghreposscorer.domain.port.out;

import com.alns.rcpharm.ghreposscorer.domain.model.GitHubRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * Output Port interface for retrieving repository data from GitHub.
 */
public interface GitHubRepositoryPort {

    List<GitHubRepository> fetchGitHubRepositories(String language, LocalDate createdAfter);

    /**
     * Fetches repositories page-by-page as a reactive stream.
     * Each emitted item represents a page of repositories.
     */
    default Flow.Publisher<List<GitHubRepository>> fetchGitHubRepositoriesPageStream(String language, LocalDate createdAfter) {
        return subscriber -> {
            if (subscriber == null) return;
            subscriber.onSubscribe(new Flow.Subscription() {
                private boolean done = false;

                @Override
                public void request(long n) {
                    if (done || n <= 0) return;
                    done = true;
                    try {
                        List<GitHubRepository> repos = fetchGitHubRepositories(language, createdAfter);
                        if (repos != null && !repos.isEmpty()) {
                            subscriber.onNext(repos);
                        }
                        subscriber.onComplete();
                    } catch (Throwable t) {
                        subscriber.onError(t);
                    }
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        };
    }
}
