package com.alns.rcpharm.ghreposscorer.quarkus.scheduler;

import com.alns.rcpharm.ghreposscorer.domain.port.in.CalculatePopularityUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class CacheWarmerScheduler {

    private static final Logger log = Logger.getLogger(CacheWarmerScheduler.class);
    private static final List<String> POPULAR_LANGUAGES = List.of("Java", "Kotlin", "Python", "JavaScript", "C#", "Go", "TypeScript");
    private static final LocalDate DEFAULT_CREATED_AFTER = LocalDate.of(2010, 1, 1);

    @Inject
    CalculatePopularityUseCase calculatePopularityUseCase;

    @Scheduled(every = "1h")
    public void warmCache() {
        log.info("Starting Quarkus background cache warmer for popular languages...");
        for (String lang : POPULAR_LANGUAGES) {
            try {
                calculatePopularityUseCase.getPopularRepositories(lang, DEFAULT_CREATED_AFTER, 30);
                log.info("Quarkus cache warmed for language: " + lang);
                Thread.sleep(6000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to warm Quarkus cache for language " + lang + ": " + e.getMessage());
            }
        }
        log.info("Quarkus cache warmer completed.");
    }
}
