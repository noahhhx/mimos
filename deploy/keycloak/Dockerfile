# Keycloak with the Mimos login theme (ADR-0010). Built from the repo root:
# the theme lives in deploy/keycloak/themes and borrows the web app's
# committed fonts, so the two never drift apart.
FROM quay.io/keycloak/keycloak:26.5

COPY deploy/keycloak/themes/mimos/ /opt/keycloak/themes/mimos/
COPY apps/web/src/fonts/newsreader/ /opt/keycloak/themes/mimos/login/resources/fonts/newsreader/
COPY apps/web/src/fonts/public-sans/ /opt/keycloak/themes/mimos/login/resources/fonts/public-sans/

# The base image's unprivileged user (uid 1000).
USER 1000
