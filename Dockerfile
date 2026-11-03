FROM eclipse-temurin:17-jdk-jammy@sha256:81336a07f69ac361c7f278183b195e32037ebdb532684996806265a3b47ef516 AS build

WORKDIR /app
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src/main ./src/main
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy@sha256:c35794d1348777e6bc310b9b7f420098ef8f37e988199549b545fded79a895a2

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 app
WORKDIR /app
COPY --from=build --chown=app:app /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
