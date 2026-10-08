# 12. intervals.icu client and translator

**Step:** 14.5 · **Depends on:** 06

The edge of the integration, with no storage and no endpoints yet.

## Read first

How `integrations/plugins` builds its outbound HTTP client, its config,
and the stub server its tests use. Follow the same shape.

## Build

- New Maven module `integrations/intervals-icu` (root `pom.xml`,
  `apps/api/pom.xml`), `@NullMarked` packages under
  `io.github.noahhhx.mimos.intervalsicu`. It depends on `core-fueling`'s
  public types only.
- Config: `mimos.intervals-icu.enabled` (default on) and
  `mimos.intervals-icu.base-url` (default `https://intervals.icu`).
- `IntervalsIcuClient` reads, with HTTP Basic (username `API_KEY`, password
  the person's key):

  | Endpoint | Fields |
  | -------- | ------ |
  | `GET /api/v1/athlete/{id}/events?category=WORKOUT` | `start_date_local`, `type`, `moving_time`, `joules`, `icu_intensity` |
  | `GET /api/v1/athlete/{id}/activities` | `start_date_local`, `type`, `moving_time`, `icu_joules`, `calories` |
  | `GET /api/v1/athlete/{id}/wellness` | `weight`, `bodyFat` |
  | `GET /api/v1/athlete/{id}` | `sex`, `weight`, `sportSettings[].ftp` |

  Its response types are package-private; nothing from intervals.icu
  leaves the module.
- `IntervalsIcuTranslator` turns events into `PLANNED` and activities into
  `COMPLETED` `TrainingSession`s with source `INTERVALS_ICU` and the
  intervals.icu id as `externalId`, folding activity types onto `RIDE`,
  `RUN`, `SWIM`, `STRENGTH`, `OTHER`. It turns wellness and the athlete
  record into profile hints (weight, body fat, sex, FTP) as a module type.

## Checks

- Translator tests on frozen JSON taken from intervals.icu's published
  OpenAPI spec: a planned ride with `joules`, one without, a completed
  activity with `icu_joules`, and wellness with `bodyFat`. (A run with only
  `calories` waits for item 18.)
- Client tests against an in-JVM stub: the Basic header, the date range
  parameters, a 401 and a 429 surfaced as distinct results.
- Gates: Java.

Land on `main`; set row 12 to `done`. Nothing calls the module yet.
