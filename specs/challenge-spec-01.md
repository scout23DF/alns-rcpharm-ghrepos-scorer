# Spec: GitHub Repository Popularity Scorer (alns-rcpharm-ghrepos-scorer)

## 1. Executive Summary & Objective
Build a production-ready REST API and CLI that fetches repositories from GitHub based on language and earliest creation date, computes a custom Popularity Score for each repository using weighted metrics and recency decay, and returns a sorted list of scored repositories. Supports both synchronous REST endpoints and real-time Server-Sent Events (SSE) reactive streams page-by-page.

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
- **Single Responsibility Modular Domain Services:**
    - `AbstractCalculatedGHReposScore`: Base scoring calculator logic and recency decay computations.
    - `ListScoredGHReposRankingService`: Synchronous ranking generation service.
    - `ListScoredGHReposRankingStreamService`: Page-by-page reactive ranking stream service emitting Top-N ranking snapshots.
    - `UpdateScoreConfigService`: Dynamic configuration update, cache invalidation, and async cache warming trigger service.
    - `CacheWarmerService`: Asynchronous cache warming service.

### Domain Records:
- `GitHubRepository(String id, String name, String fullName, String htmlUrl, String description, String language, long stars, long forks, Instant pushedAt)`
- `PopularityScore(GitHubRepository repository, double score, Instant calculatedAt)`
- `ScoreConfig(double wStars, double wForks, double wRecency, double decayLambda, Boolean shouldHandleGHApiPagination, Integer maxPagesToFetch, Long delayBetweenGHApiRequestsMillis, List<String> popularLanguages, LocalDate defaultCreatedAfter)`

### Ports:
- **Input Ports:**
    - `ListScoredGHReposRankingUseCase`: `List<PopularityScore> getPopularRepositories(String language, LocalDate createdAfter, int limit)`
    - `ListScoredGHReposRankingStreamUseCase`: `Flow.Publisher<List<PopularityScore>> getPopularRepositoriesStream(String language, LocalDate createdAfter, int limit)`
    - `UpdateScoreConfigUseCase`: `ScoreConfig updateConfig(ScoreConfig newConfig)`, `ScoreConfig getCurrentConfig()`
    - `WarmCacheUseCase`: `void warmCache()`, `CompletableFuture<Void> warmCacheAsync()`
- **Output Ports:**
    - `GitHubRepositoryPort`: `List<GitHubRepository> fetchGitHubRepositories(String language, LocalDate createdAfter)`, `Flow.Publisher<List<GitHubRepository>> fetchGitHubRepositoriesPageStream(String language, LocalDate createdAfter)`
    - `ScoreConfigStoragePort`: `ScoreConfig loadConfig()`, `void saveConfig(ScoreConfig config)`
    - `CacheInvalidatorPort`: `void invalidateCache()`

### Scoring Algorithm Formula:
$$Score = (w_{stars} \times Stars) + (w_{forks} \times Forks) + \left(w_{recency} \times \frac{100}{1 + \lambda \times DaysSinceLastPush}\right)$$
- Default values: $w_{stars} = 1.0$, $w_{forks} = 1.2$, $w_{recency} = 0.8$, $\lambda = 0.01$.

## 4. Resilience, Caching & Background Ingestion Strategy
- **GitHub API Limits & Exponential Retries:** 10 requests/minute (unauthenticated) / 30 requests/minute (authenticated via `GITHUB_TOKEN`). Automatic **3-attempt exponential backoff retry** (1s, 2s, 4s) on HTTP 429/403/5xx or network errors.
- **Graceful Fallback:** If GitHub API retries are exhausted or an error occurs on subsequent pages, pagination halts gracefully and delivers the top-N ranking constructed from all accumulated repositories fetched prior to the error.
- **Cache Strategy:**
    - **In-Memory (Caffeine):** Default L1 cache for ultra-low latency.
    - **Distributed Cache (Redis):** Configurable via active profile (`cache.provider=redis`).
- **Background Cache Warmer Scheduler (`@Scheduled`):**
    - Pre-fetches and warms cache asynchronously for popular languages (`Java`, `Kotlin`, `Python`, `C#`, `Go`, `TypeScript`).
- **Cache Invalidation:** Updating score configuration via `PUT /api/v1/config/scoring` automatically flushes repository score caches and triggers async warming.

## 5. Dynamic Configuration & REST API Endpoints
- **Popularity Search (Sync):** `GET /api/v1/repositories/popular`
    - Query Params: `language` (required), `created_after` (required, ISO `YYYY-MM-DD`), `limit` (optional, default 30).
- **Popularity Stream (Reactive SSE):** `GET /api/v1/repositories/popular/stream`
    - Returns real-time top-N ranking list snapshots as Server-Sent Events (`text/event-stream`) on each page fetched from GitHub API.
- **Dynamic Scoring Config:**
    - `GET /api/v1/config/scoring` -> Returns current weights ($w_{stars}, w_{forks}, w_{recency}, \lambda$).
    - `PUT /api/v1/config/scoring` -> Dynamically updates weights, invalidates score cache, and warms cache asynchronously.
- **OpenAPI & Swagger UI Documentation:**
    - **Spring Boot (`ghreposscorer-services-springboot`):** `/swagger-ui.html`, `/v3/api-docs`
    - **Quarkus (`ghreposscorer-services-quarkus`):** `/q/swagger-ui`, `/q/openapi`

## 6. Testing & Testcontainers
- **Unit Tests:** Pure Java unit tests in `ghreposscorer-domain-core` for scoring calculations, recency decay, page-by-page streaming, link header parsing, and config updates.
- **Integration Tests:**
    - WireMock for mocking GitHub API responses (200 OK, 403 Rate Limit, 500 Internal Error).
    - **Testcontainers:** `org.testcontainers:redis` for Redis cache adapter integration tests.

## 7. CLI Interface (`ghreposscorer-util-cli`)
- Built with **PicoCLI** (`quarkus-picocli`).
- Declares Maven dependency on `ghreposscorer-services-quarkus` to reuse `GitHubRepositoryPort` adapter directly.
- Command example: `./rcp-ghrepos-scorer-cli --language=kotlin --created-after=2026-01-01 --limit=10`
- Compiled to a standalone native binary via GraalVM 25.

## 8. Containerization, GraalVM & Kubernetes
- **Multi-Stage Dockerfiles:** `Dockerfile` (JVM OpenJDK 25) & `Dockerfile.native` (GraalVM 25 AOT).
- **Local Orchestration (`docker-compose.yml`):** Services `redis`, `ghreposscorer-services-springboot-runner`, `ghreposscorer-services-quarkus-runner`.
- **Kubernetes Manifests (`/k8s/`):** Manifests scoped under namespace `alns-rcpharm-ghrepos-scorer`.