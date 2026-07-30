# 🚀 GitHub Repository Popularity Scorer (`alns-rcpharm-ghrepos-scorer`)

> A production-ready, highly resilient microservice and CLI application built with **Java 25** and **Hexagonal Architecture**. It evaluates and ranks GitHub repositories using a custom decay-weighted popularity scoring algorithm, featuring dual Web runners (**Spring Boot 4** and **Quarkus 3**) and a native GraalVM CLI.

---

## 📐 Architecture & Module Design

The project strictly follows **Hexagonal Architecture (Ports & Adapters)** to decouple core business logic from framework dependencies.

```text
alns-rcpharm-ghrepos-scorer/
├── ghreposscorer-domain-core          # 100% Pure Java 25 Domain (Zero framework dependencies)
├── ghreposscorer-services-springboot  # Spring Boot 4 Runner (OpenFeign, Resilience4j, Spring Cache, OpenAPI)
├── ghreposscorer-services-quarkus     # Quarkus 3 Runner (RESTEasy Reactive, SmallRye Fault Tolerance, OpenAPI)
├── ghreposscorer-util-cli             # PicoCLI Native Binary (Reuses Quarkus infrastructure)
└── k8s/                               # Kubernetes Manifests (Scoped under namespace alns-rcpharm-ghrepos-scorer)
```

### Module Responsibilities

1. **`ghreposscorer-domain-core`**:
   - Contains pure Java 25 domain records (`GitHubRepository`, `PopularityScore`, `ScoreConfig`), use case interfaces (Input Ports), and outbound contract interfaces (Output Ports).
   - Holds the domain popularity algorithm, recency decay calculator, and RFC 5988 `Link` header pagination parser without any framework annotations.

2. **`ghreposscorer-services-springboot`**:
   - REST API runner exposed on port `8080`.
   - Outbound integration via **Spring Cloud OpenFeign** with **Resilience4j** Circuit Breaker and Rate Limiter.
   - Caffeine & Redis caching layer with background Cache Warmer service.

3. **`ghreposscorer-services-quarkus`**:
   - High-performance reactive REST API runner exposed on port `8081`.
   - Outbound integration via **MicroProfile REST Client Reactive** with **SmallRye Fault Tolerance**.
   - Serves as the CDI provider for the CLI module.

4. **`ghreposscorer-util-cli`**:
   - Standalone CLI utility using **PicoCLI** compiled to a native GraalVM 25 binary.
   - Directly reuses `ghreposscorer-services-quarkus` infrastructure without code duplication.

---

## 🧮 Popularity Scoring Algorithm

The popularity of a repository is calculated using weighted metrics combined with an exponential recency decay penalty based on the last push date:

$$Score = (w_{stars} \times Stars) + (w_{forks} \times Forks) + \left(w_{recency} \times \frac{100}{1 + \lambda \times DaysSinceLastPush}\right)$$

### Default Weights:
- **$w_{stars}$**: $1.0$
- **$w_{forks}$**: $1.2$
- **$w_{recency}$**: $0.8$
- **$\lambda$ (Decay Factor)**: $0.01$

> **Dynamic Configuration:** Weights can be modified at runtime via `PUT /api/v1/config/scoring`, automatically invalidating cached repository scores and triggering non-blocking background cache warming.

---

## 🛡️ GitHub API Integration, Rate Limit & Pagination

- **RFC 5988 Pagination (`Link` Header):** When enabled via `ScoreConfig.shouldHandleGHApiPagination`, the HTTP adapters dynamically follow the `rel="next"` URI provided in response headers up to `ScoreConfig.maxPagesToFetch`.
- **Rate Limit & Delay Safeguards:** Includes a configurable delay (`ScoreConfig.delayBetweenGHApiRequestsMillis`) between page requests to prevent rate limit throttling.
- **Authentication:** Supports optional GitHub Personal Access Tokens via the `GITHUB_TOKEN` environment variable to increase quota limits.

---

## 🔌 API Endpoints & Swagger Documentation

### 1. Popular Repositories Search
```http
GET /api/v1/repositories/popular?language=java&created_after=2026-01-01&limit=30
```

### 2. Get Current Scoring Configuration
```http
GET /api/v1/config/scoring
```

### 3. Update Scoring Configuration (Triggers Cache Invalidation & Async Cache Warming)
```http
PUT /api/v1/config/scoring
Content-Type: application/json

{
  "wStars": 1.5,
  "wForks": 1.0,
  "wRecency": 1.0,
  "decayLambda": 0.005,
  "defaultCreatedAfter": "2010-01-01",
  "defaultPopularityLimit": 30,
  "shouldHandleGHApiPagination": true,
  "maxPagesToFetch": 5,
  "delayBetweenGHApiRequestsMillis": 6000,
  "popularLanguages": [
    "Java",
    "Kotlin",
    "Python",
    "C#",
    "Go",
    "TypeScript"
  ]
}
```

### 📖 Interactive Swagger UI & Health Endpoints

| Service | Swagger UI / OpenAPI | Health Checks | Port |
|---|---|---|---|
| **Spring Boot** | `http://localhost:8080/swagger-ui.html` | `http://localhost:8080/actuator/health` | `8080` |
| **Quarkus** | `http://localhost:8081/q/swagger-ui/` | `http://localhost:8081/q/health` | `8081` |

---

## 🚀 Running the Project

### Prerequisites
- JDK 25
- Maven 3.9.5+
- Docker & Docker Compose (optional)

### 1. Build and Run Tests
```bash
mvn clean verify
```

### 2. Run via Docker Compose

Launches Redis, Spring Boot (Port 8080), and Quarkus (Port 8081):

```bash
docker-compose up --build
```

### 3. Run CLI Application

- **Standard Executable JAR**:
  ```bash
  java -jar ghreposscorer-util-cli/target/quarkus-app/quarkus-run.jar -l Kotlin -n 10
  ```

- **GraalVM Native Executable Binary**:
  ```bash
  ./ghreposscorer-util-cli/target/ghreposscorer-util-cli-runner -l Java -n 10
  ```

### 4. Deploy to Kubernetes

Apply all manifests scoped under the `alns-rcpharm-ghrepos-scorer` namespace:

```bash
kubectl apply -f k8s/
```

---

## 🧪 Testing Strategy

- **Domain Core:** Pure JUnit 5 & AssertJ unit tests for scoring calculations, recency decay logic, and RFC 5988 `Link` header parsing.
- **Integration Tests:** WireMock for GitHub API mocking and **Testcontainers (Redis)** for cache layer verification.