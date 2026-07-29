package com.alns.rcpharm.ghreposscorer.domain.port.in;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;

/**
 * Input Port interface for viewing and dynamically updating scoring weights.
 */
public interface UpdateScoreConfigUseCase {
    ScoreConfig updateConfig(ScoreConfig newConfig);
    ScoreConfig getCurrentConfig();
}
