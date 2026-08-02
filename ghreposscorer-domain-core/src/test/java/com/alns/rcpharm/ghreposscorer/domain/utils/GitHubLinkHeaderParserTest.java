package com.alns.rcpharm.ghreposscorer.domain.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubLinkHeaderParserTest {

    @Test
    @DisplayName("Should extract next page URI when rel='next' is present")
    void testExtractNextPageUriSuccess() {
        String linkHeader = "<https://api.github.com/search/repositories?q=language%3AJava&page=2>; rel=\"next\", <https://api.github.com/search/repositories?q=language%3AJava&page=10>; rel=\"last\"";

        Optional<URI> nextUri = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);

        assertThat(nextUri).isPresent();
        assertThat(nextUri.get()).isEqualTo(URI.create("https://api.github.com/search/repositories?q=language%3AJava&page=2"));
    }

    @Test
    @DisplayName("Should return empty when rel='next' is missing")
    void testExtractNextPageUriMissingNext() {
        String linkHeader = "<https://api.github.com/search/repositories?q=language%3AJava&page=1>; rel=\"prev\", <https://api.github.com/search/repositories?q=language%3AJava&page=10>; rel=\"first\"";

        Optional<URI> nextUri = GitHubLinkHeaderParser.extractNextPageUri(linkHeader);

        assertThat(nextUri).isEmpty();
    }

    @Test
    @DisplayName("Should return empty for null or blank link header")
    void testExtractNextPageUriNullOrBlank() {
        assertThat(GitHubLinkHeaderParser.extractNextPageUri(null)).isEmpty();
        assertThat(GitHubLinkHeaderParser.extractNextPageUri("")).isEmpty();
        assertThat(GitHubLinkHeaderParser.extractNextPageUri("   ")).isEmpty();
    }
}
