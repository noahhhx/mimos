# syntax=docker/dockerfile:1

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY .mvn/ .mvn/
COPY contracts/ contracts/
COPY core/ core/
COPY integrations/ integrations/
COPY apps/ apps/
COPY deploy/keycloak/mimos-realm.json deploy/keycloak/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-jammy

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
