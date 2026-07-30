package com.alns.rcpharm.ghreposscorer.springboot.scheduler;

import com.alns.rcpharm.ghreposscorer.domain.port.in.CalculatePopularityUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CacheWarmerScheduler {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmerScheduler.class);
    private static final List<String> POPULAR_LANGUAGES = List.of("Java", "Kotlin", "Python", "JavaScript", "C#", "Go", "TypeScript");
    private static final LocalDate DEFAULT_CREATED_AFTER = LocalDate.of(2010, 1, 1);

    private final CalculatePopularityUseCase calculatePopularityUseCase;

    public CacheWarmerScheduler(CalculatePopularityUseCase calculatePopularityUseCase) {
        this.calculatePopularityUseCase = calculatePopularityUseCase;
    }

    @Scheduled(fixedRateString = "${cache.warmer.fixed-rate:3600000}")
    public void warmCache() {
        log.info("Starting background cache warmer for popular languages...");
        for (String lang : POPULAR_LANGUAGES) {
            try {
                calculatePopularityUseCase.getPopularRepositories(lang, DEFAULT_CREATED_AFTER, 30);
                log.info("Cache warmed for language: {}", lang);
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to warm cache for language {}: {}", lang, e.getMessage());
            }
        }
        log.info("Cache warmer completed.");
    }
}
