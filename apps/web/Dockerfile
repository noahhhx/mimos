# syntax=docker/dockerfile:1

# ---- build stage ----
FROM node:22-alpine AS build
WORKDIR /build

# Install with the workspace manifests first so the layer caches.
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/
COPY libraries/api-client/package.json libraries/api-client/
COPY libraries/plugin-sdk/package.json libraries/plugin-sdk/
COPY plugins/country-week/package.json plugins/country-week/
COPY tools/harness/package.json tools/harness/
RUN --mount=type=cache,target=/root/.npm \
    npm ci --workspaces --include-workspace-root

# Sources: the client the app compiles against, the spec it is generated
# from (documentation), and the app itself.
COPY contracts/ contracts/
COPY libraries/ libraries/
COPY apps/web/ apps/web/

# No URLs are baked in: the app reads them from the container's env at run
# time (MIMOS_API_URL, MIMOS_OIDC_AUTHORITY, MIMOS_OIDC_CLIENT_ID,
# MIMOS_APP_URL, API_SERVER_URL — ADR-0012), so one image serves any host.
RUN npm run build -w @mimos/web

# ---- runtime stage ----
FROM node:22-alpine

RUN addgroup --system --gid 1001 mimos \
 && adduser --system --uid 1001 --ingroup mimos --home /app mimos

WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Standalone output mirrors the monorepo layout (server.js at apps/web).
COPY --from=build --chown=mimos:mimos /build/apps/web/.next/standalone ./
COPY --from=build --chown=mimos:mimos /build/apps/web/.next/static ./apps/web/.next/static

USER mimos
EXPOSE 3000
CMD ["node", "apps/web/server.js"]
