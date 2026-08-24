# Mimos

Mimos is a recipe companion for people who cook: a curated library of free
recipes, personal recipes with the same richness, and a straight line from
"what am I cooking this week?" to plans, shopping lists, and a picture of
what you're actually eating.

It is built for two kinds of homes at once: the person who signs up and uses
it like any website, and the person who runs it themselves on their own
hardware. Both are first-class.

- Product vision: [`NORTHSTAR.md`](https://github.com/noahhhx/mimos/blob/main/NORTHSTAR.md)
- Build plan: [`ROADMAP.md`](https://github.com/noahhhx/mimos/blob/main/ROADMAP.md)
- How we build: [`AGENTS.md`](https://github.com/noahhhx/mimos/blob/main/AGENTS.md)

## Quickstart (self-hosted)

Requires Docker.

```bash
git clone https://github.com/noahhhx/mimos.git
cd mimos/deploy/docker
docker compose up -d --wait
```

The API then serves on `http://localhost:8080` (health at
`/actuator/health`), Keycloak on `http://localhost:8081`, and Postgres on
`localhost:5432`.

## Developing

The full stack (Postgres, Keycloak, API, web) runs in Docker:

```bash
docker compose -f deploy/docker/compose.yml up -d --wait
```

- Web: <http://localhost:3000> (log in with `test` / `mimos-test`)
- API: <http://localhost:8080> (health at `/actuator/health`)
- Keycloak admin: <http://localhost:8081> (`admin` / `admin`)

Frontend development server (against the compose backend):

```bash
npm install
npm run dev -w @mimos/web
```

Checks:

```bash
./mvnw verify                                  # build + tests (Testcontainers; needs Docker)
npm run typecheck -w @mimos/web               # frontend types
npm run generate -w @mimos/api-client          # regen TS client from contracts/api/openapi.yaml
mkdocs build --strict                          # docs build (pip install -r docs/requirements.txt)
```

Architecture and conventions live in `AGENTS.md`; significant decisions are
recorded as [ADRs](decisions/index.md).
