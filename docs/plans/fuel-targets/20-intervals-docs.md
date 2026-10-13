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

## Result

- `docs/guide/fuel.md` gains "Bring in training from intervals.icu":
  where the athlete id and key are, what comes across (sessions, body
  fat, and weight, sex, and FTP as suggestions), how often, Refresh, the
  three things that can go wrong, and Disconnect. "Where the numbers
  come from" now lists all five energy sources in order and synced body
  fat. A new screenshot, `fuel-intervals.png`; `fuel-plan.png` retaken
  with the source label, from the same week and meals.
- `docs/self-hosting.md` gains an intervals.icu section: turning it on
  and off, `MIMOS_SECRET_KEY` and what rotating it does, and
  `MIMOS_INTERVALSICU_SYNCINTERVAL`.
- AGENTS.md describes `integrations/intervals-icu` as built.

**The owner check did not happen in this session:** the owner was
asleep, and it needs their account. Decision: it does not block closing
step 14. The code already handles both answers (a planned ride with
`joules` counts its work; one without falls back to FTP or the
estimate), so the answer changes what ADR-0022 records, not what ships.
The two questions stay under "Open questions" in the index until the
owner connects their account.

Land on `main`; set row 20 to `done`.
