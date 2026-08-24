# syntax=docker/dockerfile:1

# ---- build stage ----
FROM node:22-alpine AS build
WORKDIR /build

# Install with the workspace manifests first so the layer caches.
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/
COPY libraries/api-client/package.json libraries/api-client/
RUN --mount=type=cache,target=/root/.npm \
    npm ci --workspaces --include-workspace-root

# Sources: the client the app compiles against, the spec it is generated
# from (documentation), and the app itself.
COPY contracts/ contracts/
COPY libraries/ libraries/
COPY apps/web/ apps/web/

# NEXT_PUBLIC_* values are inlined at build time; the compose stack passes
# them as build args so a self-hoster can point the frontend at their own
# Keycloak and API.
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ARG NEXT_PUBLIC_KEYCLOAK_AUTHORITY=http://localhost:8081/realms/mimos
ARG NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=mimos-web
ARG NEXT_PUBLIC_APP_URL=http://localhost:3000
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL \
    NEXT_PUBLIC_KEYCLOAK_AUTHORITY=$NEXT_PUBLIC_KEYCLOAK_AUTHORITY \
    NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=$NEXT_PUBLIC_KEYCLOAK_CLIENT_ID \
    NEXT_PUBLIC_APP_URL=$NEXT_PUBLIC_APP_URL

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
