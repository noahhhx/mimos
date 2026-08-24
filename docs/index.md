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

```bash
./mvnw verify                # build + tests (Testcontainers; needs Docker)
mkdocs build --strict        # docs build (pip install -r docs/requirements.txt)
```

Architecture and conventions live in `AGENTS.md`; significant decisions are
recorded as [ADRs](decisions/index.md).
