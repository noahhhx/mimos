# Planned foods: work items

Built to [ADR-0026](../../decisions/adr-0026-planned-foods.md). A person
plans a single food (a banana, a scanned bagel) in a meal slot without
making a recipe for it. It shows in the plan, counts in the day's planned
totals and Fuel for the day, lands on the shopping list, and can be
logged.

## How to work an item

1. Take the first `todo` item whose dependencies are `done`.
2. Read AGENTS.md, ADR-0026, and the item before writing code.
3. Build what the item says. If it turns out bigger than one session, split
   it here first.
4. Run the item's checks, plus the gates below for what you touched.
5. Land on `feat/plan-foods`: conventional commits, present tense, each
   commit builds. In the item's last commit, set its row below to `done`
   with the commit's short SHA.
6. Anything out of the item's scope goes in "Found along the way".

## Items

| #  | Item | Depends on | Status |
| -- | ---- | ---------- | ------ |
| 01 | [Domain, schema, API, export](01-backend.md) | – | todo |
| 02 | [Plan page: pick, scan, show, log a food](02-web.md) | 01 | todo |
| 03 | [Guide page and scenario](03-docs-and-scenario.md) | 02 | todo |

## Gates

- Backend: `./mvnw -B verify`.
- Client: `npm run generate -w @mimos/api-client` leaves no diff.
- Web: `npm run typecheck -w @mimos/web`, `npm test -w @mimos/web`,
  `npm run build -w @mimos/web`.
- Harness: `npm run typecheck -w @mimos/harness`, `npm test -w @mimos/harness`,
  then `devenv shell -- harness up` and the scenario.
- Docs: `devenv shell -- mkdocs build --strict`.

## Found along the way

(nothing yet)
