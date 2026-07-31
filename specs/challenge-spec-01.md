# Spec: GitHub Repository Popularity Scorer (alns-rcpharm-ghrepos-scorer)

## 1. Executive Summary & Objective
Build a production-ready REST API and CLI that fetches repositories from GitHub based on language and earliest creation date, computes a custom Popularity Score for each repository using weighted metrics and recency decay, and returns a sorted list of scored repositories.

## 2. Technical Stack & Architecture
- **Project Name:** `alns-rcpharm-ghrepos-scorer`
- **JDK:** Java 25 (Utilizing Records, Sealed Interfaces, Virtual Threads, Pattern Matching)
- **Build Tool:** Maven 3.9.5 (Multi-Module Architecture)
- **Architecture:** Pure Hexagonal Architecture (Ports & Adapters)
- **Modules / Runners:**
    1. `ghreposscorer-domain-core`: 100% Pure Java 25 (0 external framework dependencies).
    2. `ghreposscorer-services-springboot`: Spring Boot 4.1.0 + OpenFeign + Resilience4j + Spring Cache + SpringDoc OpenAPI + Swagger-UI.
    3. `ghreposscorer-services-quarkus`: Quarkus 3.38.0 + JAX-RS / RESTEasy Reactive + SmallRye Fault Tolerance + Quarkus Cache + SmallRye OpenAPI + Swagger-UI.
    4. `ghreposscorer-util-cli`: Quarkus PicoCLI + GraalVM 25 Native Image binary. Reuses `ghreposscorer-services-quarkus` infrastructure without code duplication.

## 3. Domain Core Specifications (`domain-core`)
- **Purity Rule:** ZERO external framework dependencies allowed (No Spring, Quarkus, Jackson, or Lombok).

### Domain Records:
- `GitHubRepository(String id, String name, String fullName, String htmlUrl, String description, String language, long stars, long forks, Instant pushedAt)`
- `PopularityScore(GitHubRepository repository, double score, Instant calculatedAt)`
- `ScoreConfig(double wStars, double wForks, double wRecency, double decayLambda)`

### Ports:
- **Input Ports:**
    - `ListScoredGHReposRankingUseCase`: `List<PopularityScore> getPopularRepositories(String language, LocalDate createdAfter, int limit)`
    - `UpdateScoreConfigUseCase`: `ScoreConfig updateConfig(ScoreConfig newConfig)`, `ScoreConfig getCurrentConfig()`
- **Output Ports:**
    - `GitHubRepositoryPort`: `List<GitHubRepository> fetchGitHubRepositories(String language, LocalDate createdAfter)`
    - `ScoreConfigStoragePort`: `ScoreConfig loadConfig()`, `void saveConfig(ScoreConfig config)`

### Scoring Algorithm Formula:
$$Score = (w_{stars} \times Stars) + (w_{forks} \times Forks) + \left(w_{recency} \times \frac{100}{1 + \lambda \times DaysSinceLastPush}\right)$$
- Default values: $w_{stars} = 1.0$, $w_{forks} = 1.2$, $w_{recency} = 0.8$, $\lambda = 0.01$.

## 4. Resilience, Caching & Background Ingestion Strategy
- **GitHub API Limits:** 10 requests/minute (unauthenticated) / 30 requests/minute (authenticated - Check If `GITHUB_TOKEN` env var is set). Hard-cap at 1,000 items per search query.
- **Cache Strategy:**
    - **In-Memory (Caffeine):** Default L1 cache for ultra-low latency.
    - **Distributed Cache (Redis):** Configurable via active profile (`cache.provider=redis`).
- **Background Cache Warmer Scheduler (`@Scheduled`):**
    - Runs periodically in the background to pre-fetch and warm cache for top languages (`java`, `kotlin`, `python`, `javascript`, `c#`, `go`, `typescript`) and default creation date (use the first year-day from year 2010 until the current year).
- **Resilience:** Circuit Breaker & Rate Limiter on GitHub HTTP clients via Resilience4j (Spring) & SmallRye Fault Tolerance (Quarkus).
- **Cache Invalidation:** Updating score configuration via `PUT /api/v1/config/scoring` automatically flushes/invalidates repository score caches.

## 5. Dynamic Configuration & REST API Endpoints
- **Popularity Search:** `GET /api/v1/github-repositories/popular`
    - Query Params: `language` (required), `created_after` (required, ISO `YYYY-MM-DD`), `limit` (optional, default 30).
- **Dynamic Scoring Config:**
    - `GET /api/v1/config/scoring` -> Returns current weights ($w_{stars}, w_{forks}, w_{recency}, \lambda$).
    - `PUT /api/v1/config/scoring` -> Dynamically updates weights and invalidates score cache.
- **OpenAPI & Swagger UI Documentation:**
    - **Spring Boot (`ghreposscorer-services-springboot`):**
        - OpenAPI Spec: `/v3/api-docs`
        - Interactive Swagger UI: `/swagger-ui.html`
    - **Quarkus (`ghreposscorer-services-quarkus`):**
        - OpenAPI Spec: `/q/openapi`
        - Interactive Swagger UI: `/q/swagger-ui`

## 6. Testing & Testcontainers
- **Unit Tests:** Pure Java unit tests in `ghreposscorer-domain-core` covering scoring logic and decay edge cases.
- **Integration Tests:**
    - WireMock for mocking GitHub API responses (200 OK, 403 Rate Limit, 500 Internal Error).
    - **Testcontainers:** `org.testcontainers:redis` for Redis cache adapter integration tests.

## 7. CLI Interface (`ghreposscorer-util-cli`)
- Built with **PicoCLI** (`quarkus-picocli`).
- Declares Maven dependency on `ghreposscorer-services-quarkus` to reuse `GitHubRepositoryPort` adapter directly without duplication.
- Command example: `./rcp-ghrepos-scorer-cli --language=kotlin --created-after=2026-01-01 --limit=10`
- Compiled to a standalone native binary via GraalVM 25.

## 8. Containerization, GraalVM & Kubernetes
- **Multi-Stage Dockerfiles:** `Dockerfile` (JVM OpenJDK 25) & `Dockerfile.native` (GraalVM 25 AOT).
- **Local Orchestration (`docker-compose.yml`):** Services `redis`, `ghreposscorer-services-springboot-runner`, `ghreposscorer-services-quarkus-runner`.
- **Kubernetes Manifests (`/k8s/`):** Manifests scoped under namespace `alns-rcpharm-ghrepos-scorer`.