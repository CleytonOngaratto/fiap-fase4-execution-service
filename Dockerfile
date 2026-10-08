# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./

ARG NEWRELIC_AGENT_VERSION=9.4.0
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.11.0:copy \
        -Dartifact=com.newrelic.agent.java:newrelic-agent:${NEWRELIC_AGENT_VERSION}:jar \
        -DoutputDirectory=/build/newrelic -Dmdep.stripVersion=true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /deployments

RUN groupadd --system --gid 1001 app \
 && useradd  --system --uid 1001 --gid app app

COPY --from=build --chown=1001:app /build/target/quarkus-app/lib/     ./lib/
COPY --from=build --chown=1001:app /build/target/quarkus-app/*.jar    ./
COPY --from=build --chown=1001:app /build/target/quarkus-app/app/     ./app/
COPY --from=build --chown=1001:app /build/target/quarkus-app/quarkus/ ./quarkus/

# Mesmo diretório porque é ao lado do jar que o agente procura o newrelic.yml.
COPY --from=build --chown=1001:app /build/newrelic/newrelic-agent.jar ./newrelic/newrelic.jar
COPY --chown=1001:app             docker/newrelic/newrelic.yml        ./newrelic/newrelic.yml

USER 1001
EXPOSE 8080
ENV QUARKUS_HTTP_HOST=0.0.0.0

ENTRYPOINT ["java", "-javaagent:/deployments/newrelic/newrelic.jar", "-jar", "/deployments/quarkus-run.jar"]
