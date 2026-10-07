# ADR-0022: Training-aware fuel targets

- Status: Accepted
- Date: 2026-10-07
- Builds on: [ADR-0005](adr-0005-core-product-domain.md) (nutrition),
  [ADR-0011](adr-0011-account-export-import.md) (export format),
  [ADR-0019](adr-0019-households.md) (what is per person)
- Changes: the AGENTS.md "Sync" row ("design for it, don't build it
  yet"), and the promise in `docs/guide/log.md` that the log has no goals

## Context

NORTHSTAR names training-aware suggestions as the next product step:
connect training through intervals.icu and eat more on the big days,
hitting calorie and macro targets across the week. ROADMAP step 14 places
intervals.icu in core as `integrations/intervals-icu`. The owner, a
cyclist, asked to scope it as the next plugin, with daily calorie,
carbohydrate, and protein targets from current and upcoming workouts, and
goals for people who want to lose weight, gain weight, or only fuel their
training.

Three facts decide where it lives.

**The plugin contract cannot carry it.** A plugin's `subject` is a
household (ADR-0019), so it cannot tell members apart or know who pressed
a button. Panels have no input fields and no links, and plugins sit on
the internal network, so a user cannot type a weight, paste an API key,
or finish an OAuth redirect. Plugins see no nutrition, no personal
recipes, and no logs (ADR-0006), so they cannot compare a target with a
plan. Panels render on the plan page only. Each of these would be a
contract change, and together they would send per-person health data to
third-party sidecars, which is the boundary ADR-0006 drew on purpose.

**Core has no planned nutrition.** Plan entries carry no nutrition and the
plan page shows no totals. Step 10's "a planned week shows accurate
per-day totals" holds only through logs. A target is meaningless until
the plan shows what the person will eat.

**intervals.icu suits a personal API key, not OAuth, on a self-hosted
instance.** Its OAuth apps need manual approval and exact redirect URIs,
issue no refresh tokens, and allow 100 requests per user per day. Every
self-hosted instance would apply separately. A personal API key (HTTP
Basic, username `API_KEY`) is the developer's stated path for own-data
access, allows 5,000 requests a day, and is what MacroBurn, the closest
existing product, uses. API-key users get no webhooks, so Mimos polls.
The fields Mimos needs:

| Endpoint | Fields |
| -------- | ------ |
| `GET /api/v1/athlete/{id}/events?category=WORKOUT` | `start_date_local`, `type`, `moving_time`, `joules`, `icu_intensity`, `icu_training_load`, `carbs_per_hour` |
| `GET /api/v1/athlete/{id}/activities` | `start_date_local`, `type`, `moving_time`, `icu_joules`, `calories`, `carbs_used` |
| `GET /api/v1/athlete/{id}/wellness` | `weight`, `bodyFat` |
| `GET /api/v1/athlete/{id}` | `sex`, `weight`, `sportSettings[].ftp` |

Which planned workouts carry `joules` is unconfirmed; power-target rides
likely do and others likely do not.

## Decision

### Targets are a per-person core feature

A new domain module, `core/core-fueling`, owns three things, keyed by
profile id like meal logs:

- **Fuel profile.** Whether targets are on, weight in kg, sex, optional
  body-fat percentage, optional FTP in watts, goal, and protein in g/kg.
- **Training sessions.** One row per session: date, source (`MANUAL` or
  `INTERVALS_ICU`), kind (`PLANNED` or `COMPLETED`), sport, duration,
  optional work in kJ, optional intensity, and the source's external id.
- **Daily targets.** Calculated when read, never stored, so a changed
  weight, goal, or synced workout reaches every day at once. A target
  holds calories, protein, carbs, and fat, plus its basis: the energy
  availability level, fat-free mass, exercise energy, and load band.

`core-fueling` depends on no other module and knows nothing about
households, plans, or intervals.icu. `apps/api` puts a day's target next
to the caller's planned and logged totals.

Targets are off until a person turns them on. With them off, nothing on
any page changes, and the guide's "no goals to miss" stays true for that
person.

### Calories come from energy availability

Energy availability (Loucks; IOC REDs consensus 2023) is intake minus
exercise energy, per kg of fat-free mass. Mimos inverts it:

```
calories = level × fat-free mass + exercise energy
```

Each goal is a level and a default protein:

| Goal | Level (kcal/kg FFM) | Protein default (g/kg) |
| ---- | ------------------- | ---------------------- |
| Fuel my training | 45 | 1.6 |
| Lose weight gradually | 40 | 2.0 |
| Lose weight faster | 35 | 2.2 |
| Gain weight | 50 | 1.8 |

45, 40, and 35 match TrainerRoad's nutrition calculator. 50 for gain is
our estimate and has no published source; revisit it with users. No goal
goes below 35, which keeps a margin over the low-availability threshold
of 30. Protein is adjustable from 1.2 to 3.0 g/kg.

Because exercise energy is added back in full, every goal fuels the
day's training, and a deficit comes only from the rest of the day. This
needs no BMR formula, so height and age are not asked for.

Fat-free mass is weight × (1 − body fat). Body fat comes from the fuel
profile, else the latest intervals.icu `bodyFat`, else a default by sex
(15% male, 25% female, 20% unspecified). The target shows which one it
used.

Exercise energy in kcal is taken as equal to work in kJ. At a gross
efficiency near 24%, the factor of 4.184 kJ per kcal cancels out. In
order of preference, Mimos uses:

1. Completed work (`icu_joules`).
2. Planned work (`joules`).
3. FTP × intensity × duration.
4. An activity's reported `calories`.
5. For a manual session without FTP, a per-minute estimate by intensity
   and weight.

For a day, completed sessions replace planned ones.

### Macros follow published guidance, with one precedence rule

Carbohydrate ranges come from Burke et al. 2011 and the 2016
ACSM/AND/DC position, chosen by the day's total training duration:

| Load band | Training that day | Carbs (g/kg) |
| --------- | ----------------- | ------------ |
| Light | none | 3–5 |
| Moderate | under 75 minutes | 5–7 |
| High | 75 minutes to 4 hours | 6–10 |
| Very high | 4 hours or more | 8–12 |

1. Protein is the profile's g/kg × weight.
2. Carbs take what is left after protein and 25% of calories as fat,
   clamped to the band.
3. Fat takes the remainder.
4. Calories never move. If fat falls outside 20–35% of calories, carbs
   leave their band by the smallest amount that brings fat back inside.

A sketch of this rule on a 70 kg rider (15% body fat, FTP 280) gave the
following week under "Fuel my training". It kept fat within 20–35% on
every day under the fuel and loss goals. Under gain, the precedence step
moved carbs on four days.

| Day | kcal | Carbs (g/kg) | Fat (% of kcal) |
| --- | ---- | ------------ | --------------- |
| Rest | 2,678 | 5.0 | 31 |
| 4 h endurance | 5,419 | 12.0 | 30 |

Fixed fat with carbs as the remainder, which is what TrainerRoad's
calculator appears to do, gave 15.3 g/kg on the 4-hour day. That is
above the 8–12 g/kg ceiling.

### Training comes from intervals.icu or by hand

`integrations/intervals-icu` translates events, activities, wellness,
and the athlete record into training sessions and fuel profile hints at
the edge; intervals.icu types never cross into `core-fueling`.

- **Connecting.** A person connects with their athlete id and API key on
  the Fuel page. The key is encrypted at rest with AES-GCM under an
  instance key, `MIMOS_SECRET_KEY`. Compose ships a development value and
  `deploy/selfhost/.env.example` asks the operator to generate one. The
  API never returns the key, and the export leaves it out.
- **Feature gate.** `mimos.intervals-icu.enabled`, default on. With it
  off, or with no secret key, the Fuel page offers manual training only.
- **Syncing.** A scheduled job syncs each connected person from 7 days
  back to 14 days ahead, and a Refresh button syncs one person at once.
  A sync replaces that person's `INTERVALS_ICU` sessions in the range, so
  repeating it, or a crash partway through, converges on the same rows.
  A 401 from intervals.icu marks the connection broken and the Fuel page
  says so; it never fails a page load.
- **Manual training.** A person can enter sessions by hand: sport,
  duration, and an intensity of easy, endurance, or hard. A self-hoster
  without intervals.icu loses nothing but the sync.

### Where people see it

- **Fuel page** (`/app/fuel`, in the profile menu). The on/off switch,
  the fuel profile, the goal, and the intervals.icu connection.
- **Plan page.** In the Mine view, each day shows its training, the day's
  target, and the caller's planned total. The Everyone view shows no
  targets, because they are per person.
- **Log page.** The weekly table gains each day's target beside the
  logged totals.
- **Display rules.** Targets read as "Fuel for the day", next to what is
  planned or logged. No red or green, no over or under labels, no
  scores, no streaks (NORTHSTAR principle 3).

The planned total for a person is each entry they eat, times its
recipe's per-serving nutrition, times servings ÷ diners. This is the same
share Log fills in (`apps/web/src/lib/diners.ts`), so a planned day and
its logged day agree. A household of one plans 2 servings by default,
which this counts as 2 eaten. That matches what Log records today;
leftovers are out of scope.

### API, agents, export, plugins

- **API.** New per-caller endpoints under `/api/v1/me`: the fuel profile,
  training sessions, daily fuel for a date range (target, planned,
  logged, and training per day), and the intervals.icu connection with a
  sync action. Plans gain a per-caller planned-totals read.
- **Agents.** Every new endpoint is an MCP tool except connecting and
  disconnecting intervals.icu, which carry `x-mcp: false` so an agent
  never handles the key.
- **Export.** Format version 5 adds the fuel profile and manual training
  sessions. It leaves out the API key and synced sessions; a new
  connection syncs them again.
- **Plugins.** No change. Plugins never see fuel profiles, training, or
  targets.
- **Households.** All of it is per person. Joining or leaving a household
  changes nothing here.

## Alternatives considered

- **A sidecar plugin with contract extensions.** Per-person subjects,
  input fields, secret entry, a browser-reachable callback, and nutrition
  and logs in the plugin context. Five contract additions for one
  plugin, and they send health data to third-party code. Rejected.
- **A single-athlete plugin.** One instance-wide API key in the plugin's
  environment and a week panel of targets. It works only for a household
  of one on the owner's own server and still cannot compare targets with
  the plan. Rejected.
- **TDEE plus a deficit.** BMR from Mifflin–St Jeor times an activity
  factor, plus exercise, minus a fixed deficit. It needs height and age,
  double-counts exercise inside the activity factor, and spreads the
  deficit over training days. Energy availability fuels the work by
  construction.
- **Fixed fat and protein, carbs as the remainder.** Gave 15.3 g/kg of
  carbs on a 4-hour day, above published guidance.
- **OAuth as the default connection.** Needs a manually approved
  intervals.icu app per instance and exact redirect URIs. It may come
  later as an option on a hosted instance; the API key path stays.

## Consequences

- AGENTS.md gains `core/core-fueling` and the `integrations/intervals-icu`
  details, and the Sync row points here. ROADMAP step 14 is split into
  its PRs.
- `docs/guide/log.md` no longer promises "no goals" without a
  qualifier; a new guide page, `docs/guide/fuel.md`, explains targets and
  where their numbers come from.
- A person with targets off sees no change anywhere.
- Not decided yet: on-bike carbs per hour from `carbs_per_hour`, writing
  logged intake back to intervals.icu wellness (needs no new
  permission with an API key), training-aware recipe suggestions, and
  sports other than cycling beyond the calorie fallback.
