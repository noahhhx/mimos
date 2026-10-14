# Self-hosting

Run Mimos on your own server from the published images. You need Docker
with Compose 2.23.1 or later, and a reverse proxy that serves HTTPS. No
checkout or build is needed.

## What runs

| Service        | Image                                       | Port (default)      |
| -------------- | ------------------------------------------- | ------------------- |
| `web`          | `ghcr.io/noahhhx/mimos-web`                 | `127.0.0.1:3000`    |
| `api`          | `ghcr.io/noahhhx/mimos-api`                 | `127.0.0.1:8080`    |
| `keycloak`     | `ghcr.io/noahhhx/mimos-keycloak`            | `127.0.0.1:8081`    |
| `country-week` | `ghcr.io/noahhhx/mimos-country-week`        | internal only       |
| `postgres`     | `postgres:18` (databases `mimos`, `keycloak`) | internal only     |

## Tags

- `main` follows the main branch. Every green push to main updates it.
- `1.2.3`, `1.2` and `latest` come from release tags (`v1.2.3`).
- `sha-<commit>` pins one exact build of main.

Set the tag with `MIMOS_VERSION` in `.env`.

## Set up

1. Get `deploy/selfhost/compose.yml` and `deploy/selfhost/.env.example`
   into a directory on the server, and copy `.env.example` to `.env`.
2. Choose three HTTPS URLs and set them in `.env`. Subdomains are the
   simplest:

    | Variable              | Example                           | Proxies to       |
    | --------------------- | --------------------------------- | ---------------- |
    | `WEB_ORIGIN`          | `https://mimos.example.home`      | `WEB_PORT`       |
    | `API_PUBLIC_URL`      | `https://api.mimos.example.home`  | `API_PORT`       |
    | `KEYCLOAK_PUBLIC_URL` | `https://auth.mimos.example.home` | `KEYCLOAK_PORT`  |

3. Generate `POSTGRES_PASSWORD` and `KEYCLOAK_ADMIN_PASSWORD`, for example
   with `openssl rand -base64 24`, and `MIMOS_SECRET_KEY` with
   `openssl rand -base64 32`. Mimos encrypts the intervals.icu API keys
   people connect under `MIMOS_SECRET_KEY`. Keep it: if it changes, each
   connected person enters their key again. Leave it empty to turn
   intervals.icu off. A value that is not 32 bytes in base64 stops the API
   from starting.
4. If the repository's packages are private, log in once with a GitHub
   token that has `read:packages`:
   `docker login ghcr.io -u <github-user>`.
5. Start it: `docker compose up -d --wait`.

### Why HTTPS

The browser signs in with Authorization Code + PKCE, and PKCE uses the Web
Crypto API, which browsers only offer on `https://` pages and
`http://localhost`. A page served as `http://192.168.1.10:3000` cannot sign
in. A certificate from your proxy's internal CA works, as long as the
devices you use trust that CA.

### Reverse proxy

Forward each URL to its port and send the usual `X-Forwarded-For`,
`X-Forwarded-Proto` and `X-Forwarded-Host` headers. Keycloak builds its
URLs from them, and so does the API when it tells AI agents where to sign
in. The API trusts these headers only from private-network and loopback
addresses, where a proxy in front of it runs. The defaults bind to `127.0.0.1`, which suits a proxy on the
same host. If the proxy runs on another machine, set `BIND_ADDRESS=0.0.0.0`.
A Caddy example:

```
mimos.example.home {
    reverse_proxy 127.0.0.1:3000
}
api.mimos.example.home {
    reverse_proxy 127.0.0.1:8080
}
auth.mimos.example.home {
    reverse_proxy 127.0.0.1:8081
}
```

## First sign-in

The realm ships with no users. Self-registration is enabled, so the first
person can choose **Create account** on the home page. Alternatively, create users in the
Keycloak admin console at `KEYCLOAK_PUBLIC_URL/admin` as `admin` with
`KEYCLOAK_ADMIN_PASSWORD`. That admin is a temporary bootstrap account:
create a permanent admin in the `master` realm and delete it.

To close sign-up, do both of these:

1. Turn off **User registration** in the `mimos` realm's **Realm settings →
   Login**. This is what stops new accounts.
2. Set `SIGNUP=closed` in `.env` and run `docker compose up -d`. This hides
   **Create account**, which would otherwise lead to Keycloak's
   "Registration not allowed" page.

## How long people stay signed in

The realm keeps a session for 30 days without use and for a year at most,
so people sign in once per device. To change either, open the `mimos`
realm's **Realm settings → Sessions** and set **SSO Session Idle** and
**SSO Session Max**.

Keycloak imports the realm only on the first start. An instance set up
before these values were in the realm keeps Keycloak's defaults of 30
minutes and 10 hours, so people are asked to sign in on almost every
visit. Set the two values by hand to fix that: 30 days and 365 days match
a new instance. After the upgrade that brought these values, everyone
signs in once more, and from then on stays signed in.

## Changing URLs later

The web app and API read their URLs at start, so edit `.env` and
`docker compose up -d`. Keycloak imports the realm only on the first start,
so also update the `mimos-web` client's **Valid redirect URIs** and **Web
origins** in the admin console, and the link in the `mimos` realm's **HTML
Display name** (**Realm settings → General**).

The HTML display name is the Mimos brand above the sign-in and register
forms, and it links back to the web app's home page. An instance set up
before that link existed shows plain text there. To add the link, set
**HTML Display name** to `<a href="WEB_ORIGIN/">Mimos</a>`, with your
`WEB_ORIGIN` in place of `WEB_ORIGIN`.

## Upgrades and backups

`docker compose pull && docker compose up -d --wait` upgrades. Migrations
run on start and only go forward.

Everything is in the `postgres-data` volume, in two databases. Recipe
pictures are stored in the `mimos` database too, so it grows with them,
and so do its dumps:

```bash
docker compose exec -T postgres pg_dump -U mimos mimos > mimos.sql
docker compose exec -T postgres pg_dump -U mimos keycloak > keycloak.sql
```

Country of the Week keeps each household's wheel (chosen and removed
countries) in an SQLite file on the `country-week-data` volume. Copy it
out while the plugin runs:

```bash
docker compose cp country-week:/data/country-week.db country-week.db
```

Each user can also export their own data from **Your data** in the app's
profile menu (see [Your data](guide/your-data.md)).

## Plugins

The stack registers the reference plugin, Country of the Week. A
registered plugin is only *available*: someone in each household turns it
on in the Plugins page, for everyone in the household, before it sees
their week or suggests anything (see [Plugins](guide/plugins.md)).

### Adding a plugin

Run the plugin as another service in `compose.yml`, then register it on
the `api` service with the next free index:

```yaml
      MIMOS_PLUGINS_1_ID: my-plugin              # optional; must match its manifest
      MIMOS_PLUGINS_1_URL: http://my-plugin:8080
      MIMOS_PLUGINS_1_TIMEOUT: 2s                # optional, default 2s
      MIMOS_PLUGINS_1_SHAREDSECRET: ...          # optional, for plugins off this host
```

Registrations take effect on `docker compose up -d`. A misconfigured
registration, such as two plugins with the same id, stops the API from
starting, so you notice it straight away. A plugin that is down or slow
never does: it logs a warning and contributes nothing until it's back.

### Without plugins

Delete the `country-week` service, its `country-week-data` volume, and
the two `MIMOS_PLUGINS_0_*` lines from `compose.yml`.

## intervals.icu

People can connect their own intervals.icu account on the Fuel page, so
their planned workouts and completed activities feed their fuel targets
(see [Bring in training from intervals.icu](guide/fuel.md#bring-in-training-from-intervalsicu)).
Each person uses a personal API key from their intervals.icu settings, so
there is no app to register. The API reaches `https://intervals.icu`, so
the server needs outbound HTTPS.

- **Turning it on.** It is on whenever `MIMOS_SECRET_KEY` is set. Leave
  the key empty, or add `MIMOS_INTERVALSICU_ENABLED: "false"` to the `api`
  service's environment, and the Fuel page offers training entered by
  hand only.
- **The key.** Mimos encrypts each person's intervals.icu API key under
  `MIMOS_SECRET_KEY` (AES-256-GCM) and never shows it again. Generate it
  once with `openssl rand -base64 32` and back it up with your `.env`. If
  it changes, no stored key opens any more: every connection reads as
  needing its key again, and each person re-enters theirs on the Fuel
  page. Training already synced stays until they do.
- **How often it syncs.** Every 30 minutes, each connected person in
  turn; their Refresh button syncs them at once. Set
  `MIMOS_INTERVALSICU_SYNCINTERVAL` on the `api` service (such as `15m`,
  at least `1m`) to change it. A sync makes four requests to
  intervals.icu, well inside the 5,000 a day it allows each key. When
  intervals.icu asks Mimos to slow down, Mimos waits as long as it says
  before that person's next sync.

## Open Food Facts

Mimos looks packaged food up by its barcode in
[Open Food Facts](https://world.openfoodfacts.org), the open product
database, so people can log it from its name and nutrition. It needs no
key or account. The API sends Open Food Facts the barcode and nothing
else: not who asked, not their address, since the API makes the request
rather than the browser. Nothing it answers is stored. The server needs
outbound HTTPS to `world.openfoodfacts.org`.

- **Turning it off.** It is on by default. Set
  `MIMOS_OPENFOODFACTS_ENABLED=false` in `.env` and every barcode lookup
  answers "not found" without leaving your server.
- **Limits.** Open Food Facts allows 100 product lookups a minute from
  one address. Past that, or while it is down, a lookup fails and can be
  tried again.

## AI agents

Users can connect AI agents such as Claude Code to Mimos over MCP, at your
API URL followed by `/mcp` (see [Use Mimos from an AI agent](guide/agents.md)).
Agents sign in through Keycloak as the `mimos-agent` client, which the
realm includes. Claude on claude.ai connects from Anthropic's servers, so
it needs the API and Keycloak on public HTTPS URLs; Claude Code does not.

### Add the agent client to an existing instance

Keycloak imports the realm only on the first start, so an instance set up
before agent support has no `mimos-agent` client. Add it in the admin
console:

1. Open `KEYCLOAK_PUBLIC_URL/admin`, sign in as an admin, and switch to the
   `mimos` realm.
2. Go to **Clients** and choose **Create client**.
3. Set **Client ID** to `mimos-agent` and choose **Next**.
4. Leave **Client authentication** off. Under **Authentication flow**,
   tick only **Standard flow**, then choose **Next**.
5. Add these four **Valid redirect URIs**, then choose **Save**:
    - `http://localhost/callback`
    - `http://127.0.0.1/callback`
    - `http://[::1]/callback`
    - `https://claude.ai/api/mcp/auth_callback`
6. Open the client's **Advanced** tab. Under **Advanced settings**, set
   **Proof Key for Code Exchange Code Challenge Method** to `S256` and
   choose **Save**.

The loopback redirect URIs have no port on purpose: Keycloak then accepts
any port, which agents on your computer pick at random.
