package com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github;

import com.alns.rcpharm.ghreposscorer.springboot.adapter.out.github.dto.GitHubSearchResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "githubFeignClient", url = "${github.api.url:https://api.github.com}")
public interface GitHubFeignClient {

    @GetMapping(value = "/search/repositories", headers = {"Accept=application/vnd.github.v3+json"})
    ResponseEntity<GitHubSearchResponseDto> searchRepositories(
            @RequestParam("q") String query,
            @RequestParam("sort") String sort,
            @RequestParam("order") String order,
            @RequestParam("per_page") int perPage,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestHeader(value = "User-Agent", defaultValue = "alns-rcpharm-ghrepos-scorer") String userAgent,
            @RequestHeader(value = "Authorization", required = false) String authorization
    );

    @GetMapping(headers = {"Accept=application/vnd.github.v3+json"})
    ResponseEntity<GitHubSearchResponseDto> searchRepositoriesByUri(
            java.net.URI targetUri,
            @RequestHeader(value = "User-Agent", defaultValue = "alns-rcpharm-ghrepos-scorer") String userAgent,
            @RequestHeader(value = "Authorization", required = false) String authorization
    );
}
