# Keycloak with the Mimos login theme (ADR-0010) and the Mimos realm
# (ADR-0012). Built from the repo root: the theme lives in
# deploy/keycloak/themes and borrows the web app's committed fonts, so the
# two never drift apart.

# The baked-in realm is the export minus its local test users, so a
# published image never ships accounts with known passwords. The dev
# compose stack mounts the full export over it.
FROM node:22-alpine AS realm
WORKDIR /realm
COPY deploy/keycloak/mimos-realm.json ./
RUN node -e 'const fs = require("node:fs"); \
  const realm = JSON.parse(fs.readFileSync("mimos-realm.json", "utf8")); \
  delete realm.users; \
  fs.writeFileSync("mimos-realm.json", JSON.stringify(realm, null, 2));'

FROM quay.io/keycloak/keycloak:26.5

COPY deploy/keycloak/themes/mimos/ /opt/keycloak/themes/mimos/
COPY apps/web/src/fonts/newsreader/ /opt/keycloak/themes/mimos/login/resources/fonts/newsreader/
COPY apps/web/src/fonts/public-sans/ /opt/keycloak/themes/mimos/login/resources/fonts/public-sans/
# Imported on first start with --import-realm (an existing realm is left
# alone). The web origin comes from MIMOS_WEB_ORIGIN at import time.
COPY --from=realm --chown=1000:0 /realm/mimos-realm.json /opt/keycloak/data/import/mimos-realm.json

# The base image's unprivileged user (uid 1000).
USER 1000
