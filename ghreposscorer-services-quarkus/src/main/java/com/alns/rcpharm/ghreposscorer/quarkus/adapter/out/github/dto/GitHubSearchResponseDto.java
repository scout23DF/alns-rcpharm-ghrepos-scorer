package com.alns.rcpharm.ghreposscorer.quarkus.adapter.out.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
public class GitHubSearchResponseDto {
    @JsonProperty("total_count")
    private long totalCount;
    @JsonProperty("incomplete_results")
    private boolean incompleteResults;
    private List<GitHubRepositoryDto> items = new ArrayList<>();

    public GitHubSearchResponseDto() {}

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

    public boolean isIncompleteResults() { return incompleteResults; }
    public void setIncompleteResults(boolean incompleteResults) { this.incompleteResults = incompleteResults; }

    public List<GitHubRepositoryDto> getItems() { return items; }
    public void setItems(List<GitHubRepositoryDto> items) { this.items = items; }
}
