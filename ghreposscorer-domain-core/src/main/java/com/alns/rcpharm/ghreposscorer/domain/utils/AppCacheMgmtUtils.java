package com.alns.rcpharm.ghreposscorer.domain.utils;

import java.util.Locale;

public class AppCacheMgmtUtils {

    public static String generateSimpleCacheKey(String language, String createdAfter) {
        return language.toLowerCase(Locale.ROOT) + ":" + createdAfter;
    }
}
