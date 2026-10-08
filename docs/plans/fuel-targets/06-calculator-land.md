# 06. Check and land the target calculator

**Step:** 14.3 · **Depends on:** 05 · **Review:** owner decisions, no UI

## Live checks

API only, against `harness up --debug` at the head, as `test2` or a fresh
account.

1. **You see.** Profile 70 kg, male, body fat 15%, FTP 280, goal Fuel; no
   session on Monday; a 240-minute ENDURANCE ride on Saturday.
   `GET /api/v1/me/fuel` for the week returns Monday 2678 kcal and 350 g
   carbs, Saturday 5419 kcal and 840 g carbs.
2. **Body fat source.** Clear body fat, read the week; save 12%, read
   again. The basis source moves from `DEFAULT` to `PROFILE`, fat-free mass
   from 59.5 kg to 61.6 kg.
3. **Targets off.** No day has a `target`; `planned`, `logged`, and
   `sessions` are still there.
4. **MCP and HTTP agree.** `getMyFuel` through a JSON-RPC `tools/call` and
   through HTTP return equal bodies.
5. **Nothing else moved.** `GET /api/v1/logs/summary` and
   `GET /api/v1/plans/{monday}` for one seeded account are byte for byte
   the same on `main` and at the head.

The golden week for every goal, completed replacing planned, the range
400s, planned equal to logged, and household members are in the tests.

## Perf

`GET /api/v1/plans/{monday}` on `main` first, then the head. At the head,
`GET /api/v1/me/fuel` for 7 and 62 days with a seeded profile, sessions,
plans, and logs, 200 requests each.

- The plan read p95 is within 10% plus 5 ms of `main`'s.
- 7-day fuel p95 at most 120 ms; 62-day at most 400 ms.

## Review: decisions for the owner

Ask before landing; record each answer in ADR-0022 or AGENTS.md if it
changes the rule.

- **FTP prices rides only.** A run with FTP set uses the MET estimate.
  ADR-0022's wording would apply FTP to any session.
- **Protein can crowd out carbs.** With protein near 3.0 g/kg and body fat
  near 60%, protein alone passes 80% of calories; carbs stop at 0 and fat
  stays under 20%, with `carbsLeftBand` true. The alternative is a
  profile-level bound that refuses such a profile.
- **Intensity factors** EASY 0.55, ENDURANCE 0.68, HARD 0.85, and MET 4, 7,
  10 for the estimate.
- **A synced session with no work and no intensity** counts as ENDURANCE.

## Land

Fast-forward `main` and set rows 05 and 06 to `done`.
