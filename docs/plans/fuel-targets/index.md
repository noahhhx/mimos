# Training-aware fuel targets: work items

ROADMAP step 14, built to [ADR-0022](../../decisions/adr-0022-training-fuel-targets.md).
A person who turns targets on sees each day's calorie, carb, protein, and
fat target next to what they planned and logged. The targets follow their
training, from intervals.icu or entered by hand. A person with targets off
sees no change anywhere.

The first attempt ran this as five large PRs built in parallel, each checked
by a ten-lane browser swarm and perf rounds. It cost too much and stopped
partway. It left the first three parts built on local branches (see "State
the first run left"). The items below pick up from there in small pieces.

## How to work an item

1. Take the first `todo` item whose dependencies are `done`. One item per
   session.
2. Read AGENTS.md, ADR-0022, and the item before writing code.
3. Build what the item says. If it turns out bigger than one session, split
   it here first: add the new item file and its row, then carry on with the
   smaller piece.
4. Run the item's checks, plus the gates below for what you touched. Only
   one stack runs at a time, because compose binds fixed host ports.
5. An item marked **Review** changes what people see, or needs the owner's
   decision. Post its screenshots (or the question) and wait for the go
   before landing.
6. Land on `main`: conventional commits, present tense, each commit
   builds. In the item's last commit, set its row below to `done` with the
   commit's short SHA. Items 01, 03, and 05 work on a branch: they set
   their row to `on branch` in a commit on `main`, and the item that lands
   the branch sets it to `done`.
7. Anything out of the item's scope goes in "Found along the way", not
   into the item.

Status values: `todo`, `in progress`, `on branch` (code exists on a local
branch, not on `main` yet), `done <sha>`.

## Items

| #  | Item | Step | Depends on | Review | Status |
| -- | ---- | ---- | ---------- | ------ | ------ |
| 00 | [Clean up the first run](00-clean-up-first-run.md) | – | – | | todo |
| 01 | [Finish planned totals](01-planned-totals-finish.md) | 14.1 | – | | in progress |
| 02 | [Check and land planned totals](02-planned-totals-land.md) | 14.1 | 01 | Review | todo |
| 03 | [Rebase the fuel profile onto main](03-fuel-profile-rebase.md) | 14.2 | 02 | | on branch |
| 04 | [Check and land the fuel profile](04-fuel-profile-land.md) | 14.2 | 03 | Review | todo |
| 05 | [Rebase the target calculator onto main](05-calculator-rebase.md) | 14.3 | 04 | | on branch |
| 06 | [Check and land the target calculator](06-calculator-land.md) | 14.3 | 05 | Review | todo |
| 07 | [Tidy the fuel read's ranges](07-fuel-read-ranges.md) | 14.3 | 06 | | todo |
| 08 | [Fuel for the day on the plan page](08-plan-fuel-line.md) | 14.4 | 06 | Review | todo |
| 09 | [Training on the plan page](09-plan-training.md) | 14.4 | 08 | Review | todo |
| 10 | [Targets on the Log page](10-log-targets.md) | 14.4 | 08 | Review | todo |
| 11 | [The fuel guide](11-fuel-guide.md) | 14.4 | 09, 10 | Review | todo |
| 12 | [intervals.icu client and translator](12-intervals-client.md) | 14.5 | 06 | | todo |
| 13 | [A fake intervals.icu for the stack](13-intervals-fake.md) | 14.5 | 12 | Review | todo |
| 14 | [Encrypt the API key](14-secret-box.md) | 14.5 | 12 | | todo |
| 15 | [Connect and disconnect](15-intervals-connect.md) | 14.5 | 13, 14 | | todo |
| 16 | [Sync on demand](16-intervals-sync.md) | 14.5 | 15 | | todo |
| 17 | [Scheduled sync](17-intervals-schedule.md) | 14.5 | 16 | | todo |
| 18 | [Synced body fat and reported calories](18-intervals-hints.md) | 14.5 | 16 | Review | todo |
| 19 | [Connect from the Fuel page](19-intervals-ui.md) | 14.5 | 11, 17 | Review | todo |
| 20 | [Document intervals.icu and try a real account](20-intervals-docs.md) | 14.5 | 18, 19 | Review | todo |
| 21 | [Close step 14](21-close-step-14.md) | 14 | all | | todo |

Items 07, 08, and 12 only need 06, so they can go in any order after it.
Phase 5 (12 onward) is independent of phase 4 until item 19.

Between items 04 and 08, `main` carries a Fuel page whose settings show
nothing yet. The `main` image tag picks it up. That is acceptable for a
pre-release product; if it is not, land 03 to 08 together.

## Gates

Run these for what the item touched, on the committed head.

| Touched | Command |
| ------- | ------- |
| Java | `./mvnw -B verify` (Spotless, NullAway, Testcontainers). A module-scoped run always takes `-am`, e.g. `./mvnw -B -pl apps/api -am test -Dtest=X`. |
| The contract | `npm run generate -w @mimos/api-client`, commit the client; a second run leaves no diff. |
| Web | `npm ci` once, then `npm run typecheck -w @mimos/web`, `npm test -w @mimos/web`, `npm run build -w @mimos/web`. |
| Docs | `devenv shell -- mkdocs build --strict`. |
| Harness or a scenario | `npm run typecheck -w @mimos/harness` and `npm test -w @mimos/harness`. A new scenario goes into the pinned list in `tools/harness/test/args.test.ts`. |
| Anything the stack runs | `devenv shell -- harness up --debug`, the item's scenario, then every scenario the way CI's "Harness scenarios" step runs them. |

Copy rules for every new user-facing string: no em dash (AGENTS.md), and
no judging words such as over, under, missed, or exceeded (ADR-0022).
Fuel elements never use the `--danger` or `--accent` token.

## Measuring a read

Some items set a latency budget. The budgets are estimates made before any
measurement; if the first baseline shows one is wrong, change it here and
say why. Measure against the running stack, baseline first:

```sh
# Usage: probe <api-path> [requests] [user]; prints p50 and p95 in ms.
probe() {
  local path=$1 n=${2:-200} user=${3:-test} token
  token=$(devenv shell -- harness token --as "$user" 2>/dev/null)
  for _ in $(seq 5); do curl -fsS -o /dev/null -H "Authorization: Bearer $token" "http://localhost:8080$path"; done
  for _ in $(seq "$n"); do
    curl -fsS -o /dev/null -w '%{time_total}\n' -H "Authorization: Bearer $token" "http://localhost:8080$path"
  done | sort -n | awk -v p="$path" '{t[NR]=$1*1000} END {printf "path=%s n=%d p50=%.1fms p95=%.1fms\n", p, NR, t[int(NR*0.5)], t[int(NR*0.95)]}'
}
```

## State the first run left

As of 2026-10-08, nothing is on `main` yet. Branches are local only.

| Part | Branch | Head | State |
| ---- | ------ | ---- | ----- |
| 14.1 | `ft1-planned-totals` | `15bc002` | Based on `main` `93556b1`. Round 2 review fixes are half done and uncommitted in `.claude/worktrees/ft1`. Item 01. |
| 14.2 | `ft2-fuel-profile` | `5974023` | Based on `main` `93556b1`. Round 1 review fixes committed; not re-checked. Items 03, 04. |
| 14.3 | `ft3-fuel-targets` | `40ab9dc` | Its two commits (`48d84ea`, `40ab9dc`) sit on older FT1 and FT2 heads. Items 05, 06. |

The run's notes live outside the repo in `~/.claude/orchestrate/fuel-targets/`
(`prs/` holds each part's description and review findings; `decisions.tsv`
the trail). The items below copy what matters from them.

## Found along the way

Defects on `main` that the first run surfaced. None blocks step 14; promote
one to an item when you pick it up.

- At 320px wide, every signed-in page scrolls sideways by 31px: the site
  header's profile button sits past the right edge. The Log page's totals
  table reaches 376px.
- The Kitchen home and the plan page request
  `GET /api/v1/plans/{monday}/shopping-list` for an account without a list
  and get a 404 on every load. Harmless, but noisy in logs and HARs.
- The share rule (servings ÷ diners, to 0.1) exists twice:
  `PlannedDayTotal.shareOf` in core-planning and `shareOf` in
  `apps/web/src/lib/diners.ts`. Tests pin them to the same cases. One
  source (the API returns each entry's share for the caller) would remove
  the copy.

## Open questions

These stay unproven after the prototypes that settled the macro rule.

- Which intervals.icu planned workouts carry `joules`. Item 20 asks the
  owner to check with a real account.
- Whether level 50 suits weight gain. It has no published source.
- How far the default body-fat values move a person's targets from their
  measured ones.

## Rejected alternatives

ADR-0022 records the product alternatives. These are the plan-level ones.

- One change for the whole feature: too large to check, and it hides the
  planned-totals gap that 14.1 closes on its own.
- The calculator together with the profile: it would ship numbers before
  anything shows them.
- Training sessions with the calculator instead of the profile: the export
  format would bump twice, to 5 and then 6.
- The fake intervals.icu in `compose.debug.yml` only: CI boots without the
  debug overlay, so its scenario would not run in CI, and AGENTS.md
  requires every scenario to.
- Syncing on page load when stale: it puts intervals.icu's latency and
  failures on the plan page.
