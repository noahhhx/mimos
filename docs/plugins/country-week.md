# Country of the Week

Country of the Week is the reference plugin. It ships with Mimos, and the
Docker stacks register it. It is a small, complete plugin, so it is a
good place to start your own. Its source
is in
[`plugins/country-week`](https://github.com/noahhhx/mimos/tree/main/plugins/country-week).

This page walks through what it does and how each part works. For the
rules every plugin follows, see [Writing a plugin](index.md).

## What it does

Each week, the plugin picks one country and suggests library dinners
from that country's cuisine:

- It knows six countries: Italy, India, Greece, Japan, Lebanon, and
  Mexico.
- It picks only a country the library can cook. A recipe matches a
  country by a tag (`italian`) or by a word in its title (`risotto`).
  Countries with no matching recipe are skipped.
- The week decides the country. On one instance, the same week always
  gets the same country, for every user.
- It fills the week's dinner slots that are still empty, Monday first,
  with one matching recipe each, at 2 servings. It never suggests a slot
  the user already planned.
- It returns at most one card. When no country matches, or every dinner
  is already planned, it returns no card.

## How the code is laid out

The plugin is plain TypeScript on `node:http` with no runtime
dependencies. Node runs the source files directly, using its built-in
type stripping (Node 22.18 or later), so there is no build step.

| File                   | What it holds                                                    |
| ---------------------- | ---------------------------------------------------------------- |
| `src/server.ts`        | The HTTP server: the manifest, routing, and input checks.        |
| `src/country-week.ts`  | The suggestion logic, as pure functions of the request.          |
| `src/countries.ts`     | The six countries, with the tags and title words that match them. |
| `src/*.test.ts`        | Tests for the logic and for the HTTP surface.                    |
| `Dockerfile`           | The container image.                                             |

The types come from
[`@mimos/plugin-sdk`](https://github.com/noahhhx/mimos/tree/main/libraries/plugin-sdk),
which is generated from the plugin contract. The plugin needs the SDK
only to type-check. Nothing from it runs.

Keep the HTTP code and the suggestion logic apart, as this plugin does.
The logic then needs no server to test.

## The manifest

`GET /manifest` returns a fixed object. Mimos reads it when the API
starts:

```json
{
  "schema": "mimos.plugin.manifest/v1",
  "id": "country-week",
  "name": "Country of the Week",
  "version": "0.1.0",
  "apiVersions": ["1"],
  "capabilities": ["plan-suggestions"],
  "homepageUrl": "https://github.com/noahhhx/mimos"
}
```

Users see the `name` on the Plugins page and on every card. The
`homepageUrl` becomes the page's **About** link.

## Answering a suggestion request

Mimos sends `POST /v1/plan-suggestions` with the week, its planned
slots, and the library catalog. In this request, Monday's dinner is
already planned:

```json
{
  "weekStartDate": "2026-10-05",
  "plannedSlots": [
    { "date": "2026-10-05", "mealType": "DINNER", "servings": 2 }
  ],
  "libraryRecipes": [
    {
      "id": "a1",
      "title": "Spaghetti Aglio e Olio",
      "tags": ["italian", "quick"],
      "servings": 2
    },
    {
      "id": "b2",
      "title": "Mushroom Risotto",
      "tags": ["vegetarian"],
      "servings": 4
    },
    {
      "id": "c3",
      "title": "Chicken Tikka Masala",
      "tags": ["indian"],
      "servings": 4
    }
  ]
}
```

The plugin answers with one card. The first free dinner is Tuesday, so
the card starts there:

```json
{
  "suggestions": [
    {
      "title": "India week",
      "blurb": "Lean into Indian cooking with 1 dinner from the library this week.",
      "icon": "IN",
      "entries": [
        {
          "date": "2026-10-06",
          "mealType": "DINNER",
          "recipeId": "c3",
          "servings": 2
        }
      ]
    }
  ]
}
```

Mimos checks every card against the request before a user sees it. The
recipe IDs must come from `libraryRecipes`, and the dates must fall in
the week.

## Choosing the country without a clock

The plugin has no clock, no random numbers, and no storage. It counts
the days from 1970-01-01 to `weekStartDate`. It then uses that number,
modulo the count of countries the library can cook, to pick one.

This keeps the plugin simple to run and to test:

- A week shows the same card each time the plan page loads, so the
  suggestion does not change under the user.
- Restarting the plugin, or running two copies, changes nothing.
- A test passes a week and a catalog, and checks the exact answer.

The list of candidates depends on the catalog. If the instance owner adds
recipes, a week can move to a different country.

## Rejecting bad input

The server checks the request before it runs any logic. It never crashes
on bad input:

| Request                                       | Response                 |
| --------------------------------------------- | ------------------------ |
| Any method or path other than the two above   | `404`                    |
| A body that is not `application/json`         | `415`                    |
| Bad JSON, a missing field, or an invalid date | `400`                    |
| A body over 1 MiB                             | `400`                    |

Mimos treats any error response as "no suggestions" for that request.
The user sees no error.

The plugin does not check a shared secret, because it runs on the stack's
internal network. A plugin that runs on another host should check one.
Mimos sends it as `Authorization: Bearer <secret>`.

## Running and testing it

From the repository root, with Node 22.18 or later:

```bash
npm ci
npm test -w @mimos/country-week
```

To run the server, use `npm start`. It listens on port 8080, or on the
port in `PORT`:

```bash
PORT=18080 npm start -w @mimos/country-week
curl -s localhost:18080/manifest
```

In `deploy/docker`, the `country-week` service builds the plugin from
source. The `api` service registers it with `MIMOS_PLUGINS_0_ID` and
`MIMOS_PLUGINS_0_URL`.

## The container image

The `Dockerfile` has two stages. The first installs the workspace and
type-checks the plugin. The second copies only `src/` into a plain
`node:22-alpine` image and runs `node src/server.ts` as a non-root user
on port 8080. The published image is `ghcr.io/noahhhx/mimos-country-week`.

## Starting your own plugin from it

1. Copy `plugins/country-week` to a new directory and change the package
   name.
2. Give the manifest a new `id`, `name`, and `homepageUrl`.
3. Replace `country-week.ts` with your own logic. Keep it a function of
   the request.
4. Keep the input checks in `server.ts`, and add a shared-secret check if
   your plugin will run off the instance's network.
5. Register it as shown in
   [Adding a plugin](../self-hosting.md#adding-a-plugin).

Your plugin can use any language. Only the HTTP contract matters.
