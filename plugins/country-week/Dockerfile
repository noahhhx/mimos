# syntax=docker/dockerfile:1

# ---- build stage: install workspace deps (types only) and typecheck ----
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

# The plugin compiles against the plugin SDK's generated types.
COPY libraries/plugin-sdk/ libraries/plugin-sdk/
COPY plugins/country-week/ plugins/country-week/
RUN npm run typecheck -w @mimos/country-week

# ---- runtime stage ----
# Runs straight from TypeScript source on Node's native type stripping
# (engines: node >= 22.18), a deliberate zero-build choice for a small
# sidecar; the SDK is type-only, so no node_modules at runtime. Each user's
# wheel lives in a node:sqlite file under /data: mount a volume there
# (ADR-0017).
FROM node:22-alpine

RUN addgroup --system --gid 1001 mimos \
 && adduser --system --uid 1001 --ingroup mimos --home /app mimos \
 && mkdir /data \
 && chown mimos:mimos /data

WORKDIR /app
ENV NODE_ENV=production \
    PORT=8080 \
    COUNTRY_WEEK_DB=/data/country-week.db

COPY --from=build --chown=mimos:mimos /build/plugins/country-week/src ./src

USER mimos
EXPOSE 8080
# node:sqlite still warns that it is experimental on Node 22.
CMD ["node", "--disable-warning=ExperimentalWarning", "src/server.ts"]
