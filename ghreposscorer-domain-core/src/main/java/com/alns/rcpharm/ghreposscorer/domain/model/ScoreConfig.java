package com.alns.rcpharm.ghreposscorer.domain.model;

/**
 * Domain record holding weight parameters and decay factor for the popularity scoring algorithm.
 */
public record ScoreConfig(
    double wStars,
    double wForks,
    double wRecency,
    double decayLambda
) {
    public static ScoreConfig defaultConfig() {
        return new ScoreConfig(1.0, 1.2, 0.8, 0.01);
    }
}
