# Multi-stage Dockerfile based on OpenJDK 25
FROM maven:3.9.9-eclipse-temurin-25-alpine AS builder
WORKDIR /app

# Copy POM files and source modules
COPY pom.xml .
COPY ghreposscorer-domain-core ghreposscorer-domain-core
COPY ghreposscorer-services-springboot ghreposscorer-services-springboot
COPY ghreposscorer-services-quarkus ghreposscorer-services-quarkus
COPY ghreposscorer-util-cli ghreposscorer-util-cli

# Build application packages skipping tests for image creation
RUN mvn clean package -DskipTests

# Runtime stage using OpenJDK 25 JRE
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/ghreposscorer-services-springboot/target/ghreposscorer-services-springboot-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
