package com.alns.rcpharm.ghreposscorer.domain.port.out;

import com.alns.rcpharm.ghreposscorer.domain.model.ScoreConfig;

/**
 * Output Port interface for loading and storing scoring configuration.
 */
public interface ScoreConfigStoragePort {
    ScoreConfig loadConfig();
    void saveConfig(ScoreConfig config);
}
