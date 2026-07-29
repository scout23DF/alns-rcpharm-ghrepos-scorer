# AGY Agent Instructions - alns-rcpharm-ghrepos-scorer

- Project Name: `alns-rcpharm-ghrepos-scorer`.
- Strictly adhere to `/specs/challenge-spec-01.md`.
- Target JDK 25 and Maven 3.9.5 multi-module structure:
  - `ghreposscorer-domain-core`
  - `ghreposscorer-services-springboot`
  - `ghreposscorer-services-quarkus`
  - `ghreposscorer-util-cli`
- Keep `ghreposscorer-domain-core` 100% pure Java 25 without any external framework annotations.
- In `hreposscorer-util-cli`, use `quarkus-picocli` and depend on `app-quarkus` to reuse outbound adapters without duplicating code.
- Provide dynamic scoring configuration endpoints (`PUT/GET /api/v1/config/scoring`) with automatic cache invalidation.
- Provide background `@Scheduled` cache warmer logic.
- Provide Testcontainers-based tests with `RedisContainer`.
- Provide Dockerfile (JVM), Dockerfile.native (GraalVM 25), `docker-compose.yml`, and Kubernetes manifests under `/k8s/` using namespace `alns-rcpharm-ghrepos-scorer`.