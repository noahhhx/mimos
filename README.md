# Mimos

A recipe companion for people who cook: a curated library of free recipes,
your own recipes with the same richness, and a straight line from "what am I
cooking this week?" to plans, shopping lists, and a picture of what you're
actually eating.

Self-hostable by design — every feature works the same whether Mimos runs in
our cloud or on your own hardware.

**Status: pre-alpha.** The repository currently contains the backend
skeleton. See [ROADMAP.md](ROADMAP.md) for where this is going and
[NORTHSTAR.md](NORTHSTAR.md) for the product vision.

## Developing

```bash
./mvnw -B verify          # backend build + tests (Testcontainers; needs Docker)
mkdocs build --strict     # docs build (pip install -r docs/requirements.txt)
```

Architecture and conventions live in [AGENTS.md](AGENTS.md); significant
decisions are recorded as [ADRs](docs/decisions/index.md).

## License

[GPL-3.0](LICENSE)
