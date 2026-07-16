# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && \
    ./gradlew :bootstrap:bootJar --build-cache --no-daemon --console=plain && \
    cp "$(find bootstrap/build/libs -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S saqz && adduser -S -G saqz saqz
WORKDIR /app

COPY --from=build --chown=saqz:saqz /app.jar app.jar

USER saqz
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
