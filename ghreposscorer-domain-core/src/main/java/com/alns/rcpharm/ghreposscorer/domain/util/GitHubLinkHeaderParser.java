package com.alns.rcpharm.ghreposscorer.domain.util;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing RFC 5988 Link headers from GitHub REST API.
 */
public final class GitHubLinkHeaderParser {

    private static final Pattern NEXT_LINK_PATTERN = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    private GitHubLinkHeaderParser() {
        // Utility class
    }

    /**
     * Extracts the URI for rel="next" from the RFC 5988 Link header if present.
     *
     * @param linkHeader Value of the HTTP 'Link' header
     * @return Optional containing the URI of the next page, or empty if not found
     */
    public static Optional<URI> extractNextPageUri(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = NEXT_LINK_PATTERN.matcher(linkHeader);
        if (matcher.find()) {
            try {
                return Optional.of(URI.create(matcher.group(1)));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
