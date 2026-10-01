# Mimos

A recipe companion for people who cook: a curated library of free recipes,
your own recipes with the same richness, and a straight line from "what am I
cooking this week?" to plans, shopping lists, and a picture of what you're
actually eating.

Self-hostable by design — every feature works the same whether Mimos runs in
our cloud or on your own hardware.

**Status: alpha.** The core product works end to end and self-hosts from
compose: a seeded recipe library (public SEO pages), personal recipes,
weekly meal planning, generated shopping lists, calorie/macro logging, and
a plugin system proven by the Country of the Week reference plugin (plan
suggestions). See [ROADMAP.md](ROADMAP.md) (phases 0–3 done) and
[NORTHSTAR.md](NORTHSTAR.md) for the product vision.

## Quickstart (self-hosted)

Requires Docker.

```bash
git clone https://github.com/noahhhx/mimos.git
cd mimos/deploy/docker
docker compose up -d --wait
```

- Web app: <http://localhost:3000> (log in with `test` / `mimos-test`)
- Public recipe library: <http://localhost:3000/recipes>
- API: <http://localhost:8080> (health at `/actuator/health`)
- Keycloak: <http://localhost:8081> (admin console; `admin`/`admin`)
- Postgres: `localhost:5432` (`mimos`/`mimos`)

Credentials are dev defaults; override via environment variables in
[deploy/docker/compose.yml](deploy/docker/compose.yml).

## Developing

Nix users can get every toolchain (JDK 21, Node 22, MkDocs, Docker CLI) from
[devenv](https://devenv.sh): run `devenv shell`, or `direnv allow` once to
enter it automatically. Docker itself still needs a host daemon.

```bash
./mvnw -B verify          # backend build + tests (Testcontainers; needs Docker)
npm test -w @mimos/country-week   # reference plugin tests (Node 22.18+)
mkdocs build --strict     # docs build (pip install -r docs/requirements.txt)
```

Architecture and conventions live in [AGENTS.md](AGENTS.md); significant
decisions are recorded as [ADRs](docs/decisions/index.md).

Frontend development server (against the compose backend):

```bash
npm install
npm run dev -w @mimos/web
```

## License

[GPL-3.0](LICENSE)
