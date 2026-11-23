# syntax=docker/dockerfile:1

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build

COPY pom.xml ./
COPY .mvn/ .mvn/
COPY contracts/ contracts/
COPY core/ core/
COPY integrations/ integrations/
COPY apps/ apps/
COPY deploy/keycloak/mimos-realm.json deploy/keycloak/

# A release tag's version (CI's publish job), so the API reports it (ADR-0029).
# Empty builds the version in pom.xml, a snapshot.
ARG MIMOS_VERSION=

RUN --mount=type=cache,target=/root/.m2 \
    if [ -n "$MIMOS_VERSION" ]; then \
      mvn -B -q org.codehaus.mojo:versions-maven-plugin:2.18.0:set \
        -DnewVersion="$MIMOS_VERSION" -DgenerateBackupPoms=false; \
    fi \
 && mvn -B -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:22-jre-jammy

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 1001 mimos \
 && useradd --system --uid 1001 --gid mimos --home /app --shell /usr/sbin/nologin mimos

WORKDIR /app
COPY --from=build /build/apps/api/target/mimos-api-*.jar app.jar

USER mimos
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
