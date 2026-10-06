# Country of the Week

Country of the Week is the reference plugin. It ships with Mimos, and the
Docker stacks register it. It uses both plugin capabilities and keeps its
own data about each household, so it shows every part of the contract. Its
source is in
[`plugins/country-week`](https://github.com/noahhhx/mimos/tree/main/plugins/country-week).

This page walks through what it does and how each part works. For the
rules every plugin follows, see [Writing a plugin](index.md). The design
behind week panels and the `subject` pseudonym is
[ADR-0017](https://github.com/noahhhx/mimos/blob/main/docs/decisions/adr-0017-plugin-week-panels.md).

## What it does

Each week on the plan page gets a Country of the Week panel. The user
spins a wheel of countries, and the wheel stops on one:

- The user can **choose** that country. It is locked to the week, and the
  week shows its flag.
- The user can **skip** it. The wheel spins again, and the country stays
  on the wheel for later weeks.
- The user can **remove** it from the wheel for good. The wheel then
  spins again.

The wheel holds 197 countries: the 193 UN members plus Vatican City,
Palestine, Taiwan, and Kosovo. A country chosen for one week never comes
up again. A spin never lands on the continent of the country the user
chose last.

Choosing a country adds no meals. Once a week has a country, the plan
page's Suggestions panel offers library dinners from that country's
cuisine. The user decides whether to add them.

## How the code is laid out

The plugin is plain TypeScript on `node:http` and `node:sqlite`, with no
runtime dependencies. Node runs the source files directly, using its
built-in type stripping (Node 22.18 or later), so there is no build step.

| File                  | What it holds                                                          |
| --------------------- | ---------------------------------------------------------------------- |
| `src/server.ts`       | The HTTP server: the manifest, routing, input checks, and the random source. |
| `src/wheel.ts`        | The wheel, as pure functions of one household's saved choices.         |
| `src/store.ts`        | The SQLite store that keeps each household's choices and removals.     |
| `src/country-week.ts` | The suggestion card for a week's chosen country.                       |
| `src/countries.ts`    | The 197 countries, their continents, and the tags that match them.     |
| `src/*.test.ts`       | Tests for each file above.                                             |
| `Dockerfile`          | The container image.                                                   |

The types come from
[`@mimos/plugin-sdk`](https://github.com/noahhhx/mimos/tree/main/libraries/plugin-sdk),
which is generated from the plugin contract. The plugin needs the SDK
only to type-check. Nothing from it runs.

Only `server.ts` and `store.ts` touch the outside world. The wheel and
the card are pure functions, so their tests need no server and no
database.

## The manifest

`GET /manifest` returns a fixed object. Mimos reads it when the API
starts:

```json
{
  "schema": "mimos.plugin.manifest/v1",
  "id": "country-week",
  "name": "Country of the Week",
  "version": "0.2.0",
  "apiVersions": ["1"],
  "capabilities": ["plan-suggestions", "week-panel"],
  "homepageUrl": "https://github.com/noahhhx/mimos"
}
```

`week-panel` gives the plugin a panel on the plan page for each week.
`plan-suggestions` lets it offer recipe cards. Users see the `name` on
the Plugins page, on the panel, and on every card. The `homepageUrl`
becomes the Plugins page's **About** link.

## Remembering each household

The wheel needs memory. The plugin must know which countries a household
has chosen and removed. Mimos never tells a plugin who anyone is. It
sends a `subject` instead: a random UUID for this household and this
plugin. A user on their own is a household of one, and a family that
shares a household shares one wheel. Each plugin gets a different
`subject` for the same household, and it does not change when the
plugin is turned off and on again. See [Writing a plugin](index.md) for
what the `subject` is and is not.

Country of the Week keys all its data on the `subject`. `store.ts` keeps
two tables in SQLite:

- `choices` holds one row for each week a household locked to a country.
  A household can lock a week to one country, and a country to one week.
  Rows keep the order they were made in, because the continent rule
  needs the latest choice.
- `removed` holds the countries a household took off the wheel.

The store has two operations. `ledger(subject)` reads one household's choices
and removals. `apply(subject, week, effect)` makes one write. Every write
uses `ON CONFLICT DO NOTHING` or a `DELETE`, so a repeated write changes
nothing. A country code in the database that the plugin no longer knows
is skipped on read.

The store uses `node:sqlite`, which is built into Node. The plugin stays
at zero npm dependencies, and the image needs no native build. Its calls
are synchronous, so one request's read and write never interleave with
another request's. Node 22 still marks `node:sqlite` as experimental, so
the scripts and the image run Node with
`--disable-warning=ExperimentalWarning`.

The `COUNTRY_WEEK_DB` environment variable names the database file. The
image sets it to `/data/country-week.db`, and both compose stacks mount
the `country-week-data` volume at `/data`. Without `COUNTRY_WEEK_DB`, the
plugin keeps its data in memory and logs that choices last until it
stops.

The data belongs to the plugin. Mimos does not export it with a user's
account, and does not delete it. Losing the volume forgets every user's
wheel, and nothing else.

## The wheel as a state machine

`wheel.ts` models one week as one of four states:

```ts
export type WeekState =
  | { kind: "ready"; pool: Pool; segments: readonly Country[] }
  | { kind: "landed"; spin: Spin }
  | { kind: "locked"; country: Country }
  | { kind: "exhausted"; removed: number };
```

- **ready**: the week has no country, and there is something to spin.
- **landed**: the wheel just stopped on a country. Nothing is saved yet.
- **locked**: the user chose a country for this week.
- **exhausted**: no country is left to spin.

`step(ledger, week, action, rng)` takes the user's saved data and the
button they pressed. It returns the week's next state and at most one
`Effect`, the write that the action implies. `step` writes nothing
itself. The server reads the ledger, calls `step`, and hands the effect
to the store:

```ts
function weekPanel(store: Store, rng: Rng, request: WeekPanelRequest): WeekPanel {
  const { effect, state } = step(store.ledger(request.subject), request.weekStartDate, request.action, rng);
  if (effect !== null) {
    store.apply(request.subject, request.weekStartDate, effect);
  }
  return panel(state);
}
```

Each action maps to one effect:

| Action    | When it applies                             | Effect      | Next state                    |
| --------- | ------------------------------------------- | ----------- | ----------------------------- |
| none      | Always                                      | None        | The week as it stands         |
| `spin`    | The week has no country                     | None        | `landed`                      |
| `skip`    | The week has no country                     | None        | `landed`, on another country  |
| `choose`  | The week has no country, and the country can come up | `choose` | `locked`               |
| `remove`  | The week has no country, and the country is still on the wheel | `remove` | `landed` |
| `change`  | The week is locked                          | `unchoose`  | `ready`                       |
| `restore` | The user removed countries                  | `restore`   | The week as it stands         |

A request without an action renders the week and writes nothing. Mimos
sends one each time the plan page loads, so a reload can never change a
user's wheel.

Action ids and values pass through the user's browser, so the plugin
treats them as untrusted. An action that does not fit the ledger writes
nothing and renders the week again. Examples are a `choose` for a country
that cannot come up, a `change` on a week with no country, and an
unknown id. The tests check each of these.

A spin is not saved. Only `choose`, `remove`, `change`, and `restore`
write. If the user reloads after a spin, the week is `ready` again.

## Which countries a spin can land on

`poolOf(ledger)` decides which countries a spin can land on:

1. A country chosen for any week is out.
2. A country the user removed is out.
3. The continent of the most recent choice sits out. "Most recent" means
   the last choice the user made, not the latest week.
4. If every remaining country is on that continent, the continent stays
   in.

When nothing is left after these rules, the week is `exhausted`.

A `skip` spins again without the skipped country. If the skipped country
is the only one left, it can come up again.

A wheel shows at most 24 segments. `sample` draws them at random from
the countries that can come up, and `spin` lands on one of them at
random. Every country that can come up is equally likely, including
those not drawn into this wheel's 24.

## Randomness comes from the server

Every function in `wheel.ts` that needs randomness takes an `rng`
argument of type `Rng`, a function that returns a number in `[0, 1)`.
The server passes `Math.random`. The tests pass a seeded generator, so
every spin in a test repeats:

```ts
createPluginServer(openStore(path), Math.random)
```

The plugin decides where the wheel stops. The web app only animates to
the `landing` index it is given.

## The panel for each state

`panel(state)` turns a state into a declarative panel. The blocks and
their limits are in [Week panels](index.md#week-panels).

| State       | Blocks                                                                                   |
| ----------- | ---------------------------------------------------------------------------------------- |
| `ready`     | `text` with the count of countries left and the continent rule, a still `wheel`, and a **Spin** button. |
| `landed`    | A `wheel` with `landing`, a `highlight` with the country, and **Choose**, **Skip**, and **Remove from wheel** buttons. |
| `locked`    | A `summary` with the flag, a `highlight` with the country, `text` that points to Suggestions, and a **Change country** button. |
| `exhausted` | `text`, and a **Put removed countries back** button when the user removed any.           |

A `wheel` block needs at least two segments. With one country left, the
panel has no wheel. Each segment's icon is the country's flag emoji.

When the user presses **Choose Peru**, Mimos sends this request:

```json
{
  "subject": "6f1c1d2e-0000-4000-8000-000000000001",
  "weekStartDate": "2026-10-05",
  "action": { "id": "choose", "value": "PE" }
}
```

The plugin saves the choice and answers with the `locked` panel:

```json
{
  "summary": { "icon": "🇵🇪", "label": "Peru" },
  "blocks": [
    { "type": "highlight", "icon": "🇵🇪", "title": "Peru", "text": "South America" },
    {
      "type": "text",
      "text": "Peruvian recipes from the library show up under Suggestions when there are any."
    },
    { "type": "actions", "actions": [{ "id": "change", "label": "Change country" }] }
  ]
}
```

The `summary` is the line shown while the panel is collapsed. Only a
locked week sends one, so a week shows its flag only after the user
chooses a country.

## Suggestions follow the chosen country

Mimos sends `POST /v1/plan-suggestions` with the `subject`, the week, its
planned slots, and the library catalog. The plugin looks up the country
chosen for that week. With no country, or no `subject`, it returns no
cards.

With a country, `buildCard` in `country-week.ts` builds one card:

- A recipe matches a country by a tag or by a word in its title. Six
  countries have hand-tuned matchers for the seeded library. Italy, for
  example, matches the `italian` tag and titles with `risotto`. Every
  other country matches its cuisine as a tag, such as `peruvian`.
- The card fills the week's dinner slots that are still empty, Monday
  first, with one matching recipe each, at 2 servings. It never suggests
  a slot the user already planned.
- When no recipe matches, or every dinner is already planned, there is no
  card.

The card's title is `Peru week`, and its icon is the flag. Mimos checks
every card against the request before a user sees it.

## Rejecting bad input

The server checks every request before it runs any logic:

| Request                                                    | Response |
| ---------------------------------------------------------- | -------- |
| Any method or path other than the three endpoints          | `404`    |
| A body that is not `application/json`                      | `415`    |
| Bad JSON, a missing field, or an invalid date              | `400`    |
| A `subject` that is not a UUID                             | `400`    |
| An action whose `id` or `value` breaks the contract's limits | `400`  |
| A body over 1 MiB                                          | `400`    |
| An error while handling a valid request                    | `500`    |

Mimos treats a failed suggestion request as "no suggestions", and a
failed panel render as no panel. When a button press fails, Mimos tells
the user that nothing happened.

The plugin does not check a shared secret, because it runs on the stack's
internal network. A plugin that runs on another host should check one.
Mimos sends it as `Authorization: Bearer <secret>`.

## Running and testing it

From the repository root, with Node 22.18 or later:

```bash
npm ci
npm test -w @mimos/country-week
```

The tests run on `node --test`. The server and store tests use an
in-memory database, and one store test writes a file to check that the
data survives a restart.

To run the server, use `npm start`. It listens on port 8080, or on the
port in `PORT`. Set `COUNTRY_WEEK_DB` to keep choices across restarts:

```bash
PORT=18080 COUNTRY_WEEK_DB=/tmp/country-week.db npm start -w @mimos/country-week
curl -s localhost:18080/manifest
```

To render a week as a household would see it, send any UUID as the `subject`:

```bash
curl -s localhost:18080/v1/week-panel \
  -H 'Content-Type: application/json' \
  -d '{"subject":"6f1c1d2e-0000-4000-8000-000000000001","weekStartDate":"2026-10-05","action":{"id":"spin"}}'
```

In `deploy/docker`, the `country-week` service builds the plugin from
source and mounts the `country-week-data` volume. The `api` service
registers it with `MIMOS_PLUGINS_0_ID` and `MIMOS_PLUGINS_0_URL`.

## The container image

The `Dockerfile` has two stages. The first installs the workspace and
type-checks the plugin. The second copies only `src/` into a plain
`node:22-alpine` image and runs `node src/server.ts` as a non-root user
on port 8080. It creates `/data` for the database and sets
`COUNTRY_WEEK_DB=/data/country-week.db`. The published image is
`ghcr.io/noahhhx/mimos-country-week`.

## Starting your own plugin from it

1. Copy `plugins/country-week` to a new directory and change the package
   name.
2. Give the manifest a new `id`, `name`, and `homepageUrl`. List only the
   capabilities you serve.
3. Replace `wheel.ts` and `country-week.ts` with your own logic. Keep it
   pure: take the stored data, the request, and any random source as
   arguments, and return the state and the write to make.
4. Keep the store the only code that writes, and key its rows on
   `subject`. If your plugin keeps no data, delete `store.ts`.
5. Keep the input checks in `server.ts`, and add a shared-secret check if
   your plugin will run off the instance's network.
6. If your plugin keeps data, give it a volume, and tell the instance
   owner that losing it loses what your plugin remembers.
7. Register it as shown in
   [Adding a plugin](../self-hosting.md#adding-a-plugin).

Your plugin can use any language and any storage. Only the HTTP contract
matters.
