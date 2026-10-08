# 20. Document intervals.icu and try a real account

**Step:** 14.5 · **Depends on:** 18, 19 · **Review:** owner's account

## Build

- `docs/guide/fuel.md`: connecting intervals.icu (where to find the
  athlete id and API key), what syncs and how often, Refresh, Disconnect,
  and what a broken connection means. A screenshot of the connected Fuel
  page.
- `docs/self-hosting.md`: `MIMOS_SECRET_KEY` (generate with
  `openssl rand -base64 32`; without it the integration is off), the
  `mimos.intervals-icu.*` settings, and that rotating the key makes every
  stored intervals.icu key unreadable, so people connect again.
- AGENTS.md: `integrations/intervals-icu` is no longer planned; record
  anything the items above decided.

## Owner check

Ask the owner to connect their own intervals.icu account on a local stack
and say whether their planned rides carry `joules`. ADR-0022 lists that as
unconfirmed. Record the answer in ADR-0022, and remove it from "Open
questions" in the index.

## Checks

- `devenv shell -- mkdocs build --strict`.

Land on `main`; set row 20 to `done`.
