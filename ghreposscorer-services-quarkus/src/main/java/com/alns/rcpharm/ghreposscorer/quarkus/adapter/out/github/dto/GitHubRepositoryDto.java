package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

@RegisterForReflection
public class GitHubRepositoryDto {
    private String id;
    private String name;
    @JsonProperty("full_name")
    private String fullName;
    @JsonProperty("html_url")
    private String htmlUrl;
    private String description;
    private String language;
    @JsonProperty("stargazers_count")
    private long stars;
    @JsonProperty("forks_count")
    private long forks;
    @JsonProperty("pushed_at")
    private Instant pushedAt;

    public GitHubRepositoryDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public long getStars() { return stars; }
    public void setStars(long stars) { this.stars = stars; }

    public long getForks() { return forks; }
    public void setForks(long forks) { this.forks = forks; }

    public Instant getPushedAt() { return pushedAt; }
    public void setPushedAt(Instant pushedAt) { this.pushedAt = pushedAt; }
}
