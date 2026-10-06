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
   with `openssl rand -base64 24`.
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
person can use **Register** on the sign-in page. Alternatively, create users in the
Keycloak admin console at `KEYCLOAK_PUBLIC_URL/admin` as `admin` with
`KEYCLOAK_ADMIN_PASSWORD`. That admin is a temporary bootstrap account:
create a permanent admin in the `master` realm and delete it. To turn
self-registration off, use the `mimos` realm's **Realm settings → Login**.

## Changing URLs later

The web app and API read their URLs at start, so edit `.env` and
`docker compose up -d`. Keycloak imports the realm only on the first start,
so also update the `mimos-web` client's **Valid redirect URIs** and **Web
origins** in the admin console.

## Upgrades and backups

`docker compose pull && docker compose up -d --wait` upgrades. Migrations
run on start and only go forward.

Everything is in the `postgres-data` volume, in two databases:

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
