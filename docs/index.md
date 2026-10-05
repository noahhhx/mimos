---
hide:
  - navigation
  - toc
---

<div class="mimos-hero" markdown>

<p class="mimos-eyebrow">Documentation</p>

# Mimos

<p class="mimos-lede">A recipe companion for people who cook: a curated library of free
recipes, personal recipes with the same richness, and a straight line from
"what am I cooking this week?" to plans, shopping lists, and a picture of
what you're actually eating.</p>

[Self-host Mimos](self-hosting.md){ .md-button .md-button--primary }
[Write a plugin](plugins/index.md){ .md-button }

</div>

It is built for two kinds of homes at once: the person who signs up and uses
it like any website, and the person who runs it themselves on their own
hardware. Both are first-class.

## Where to start

<div class="grid cards" markdown>

-   :material-server-outline: **[Self-hosting](self-hosting.md)**

    ---

    Run Mimos on your own server from the published images, behind your
    own TLS proxy.

-   :material-puzzle-outline: **[Plugins](plugins/index.md)**

    ---

    Build a sidecar that suggests meals for the week, like the reference
    Country of the Week plugin.

-   :material-palette-outline: **[Design](design/index.md)**

    ---

    Evening Kitchen: the tokens, type, and layout rules every page is
    built from.

-   :material-console: **[Agent harness](harness/index.md)**

    ---

    Deploy, drive, observe, and debug the running stack from one command.

-   :material-scale-balance: **[Decisions](decisions/index.md)**

    ---

    Architecture decision records: what we chose, and why it was right at
    the time.

-   :material-github: **[Source](https://github.com/noahhhx/mimos)**

    ---

    The product vision in [`NORTHSTAR.md`](https://github.com/noahhhx/mimos/blob/main/NORTHSTAR.md),
    the build plan in [`ROADMAP.md`](https://github.com/noahhhx/mimos/blob/main/ROADMAP.md),
    and how we build in [`AGENTS.md`](https://github.com/noahhhx/mimos/blob/main/AGENTS.md).

</div>

## Quickstart

To run Mimos on a server from the published images, follow
[Self-hosting](self-hosting.md). To try it from a checkout (requires
Docker):

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

| Service        | URL                     | Notes                                |
| -------------- | ----------------------- | ------------------------------------ |
| Web            | <http://localhost:3000> | Log in with `test` / `mimos-test`    |
| API            | <http://localhost:8080> | Health at `/actuator/health`         |
| Keycloak admin | <http://localhost:8081> | `admin` / `admin`                    |

Frontend development server (against the compose backend):

```bash
npm install
npm run dev -w @mimos/web
```

Checks:

```bash
./mvnw verify                              # build + tests (Testcontainers; needs Docker)
npm run typecheck -w @mimos/web            # frontend types
npm run generate -w @mimos/api-client      # regen TS client from contracts/api/openapi.yaml
npm run generate -w @mimos/plugin-sdk      # regen plugin SDK from contracts/plugins/
npm test -w @mimos/country-week            # reference plugin tests (Node 22.18+)
mkdocs build --strict                      # docs build (pip install -r docs/requirements.txt)
```

Architecture and conventions live in `AGENTS.md`; significant decisions are
recorded as [ADRs](decisions/index.md).
