# 04. Check and land the fuel profile

**Step:** 14.2 · **Depends on:** 03 · **Review:** yes

## Live checks

Fresh accounts unless stated. Screenshots at 1280px.

1. **Export unchanged otherwise.** Export the same seeded account (recipes,
   plans, logs) on `main` before this lands and at the head. The only
   differences are `version` 4 to 5 and two empty new sections.
2. **Save and reload.** Open Fuel from the profile menu, save 70 kg, male,
   FTP 280, goal "Fuel my training", reload. Every value survives with
   protein 1.6 and matches `GET /api/v1/me/fuel-profile`. Save
   `fuel-saved.png`.
3. **Invalid values.** Weight 10, body fat 80, protein 4, then save. Each
   field shows its own error in `--danger`, the API answered 400
   problem-details, and a reload shows the old values. Save
   `fuel-invalid.png`.
4. **Training over the API.** Add, list, and delete a manual session with
   `harness api`; a 0-minute session is a 400; `tools/list` over MCP shows
   the fuel-profile and training operations.
5. **Import.** Export an account with a profile and two manual sessions,
   import it into a fresh account on the Your data page. The summary names
   the fuel settings and two sessions; the second account's Fuel page and
   training match.
6. **Mobile.** The Fuel page at 390px in light and dark: nothing scrolls
   sideways. Save `fuel-mobile-light.png` and `fuel-mobile-dark.png`.

Already passed on the earlier head and untouched by the fixes, so not
repeated: goal change sets protein to the goal's default, an empty profile
loads with targets off, the household join and leave keep each person's
values. `test2` getting 404 for `test`'s session is in `FuelEndpointTests`.

## Perf

On `main` first, then the head: `GET /api/v1/account/export` for an account
with 30 recipes, four planned weeks, and 100 logs, 50 requests. At the
head, `GET /api/v1/me/fuel-profile`, 200 requests.

- The head's export p95 is within 15% of `main`'s.
- The fuel-profile p95 is at most 50 ms.

## Review

Post the screenshots. Say plainly that the Fuel page's settings do nothing
visible until item 08 lands. Wait for the go.

## Land

Fast-forward `main` to the head and set rows 03 and 04 to `done`.
