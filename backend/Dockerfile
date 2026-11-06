FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system scrapestudio \
    && useradd --system --gid scrapestudio --home-dir /app scrapestudio

WORKDIR /app
COPY --from=build /build/target/scrape-studio-api-*.jar /app/app.jar
RUN mkdir -p /app/data && chown -R scrapestudio:scrapestudio /app

USER scrapestudio
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
