# Self-hosting

Run Mimos on your own server from the published images. You need Docker
with Compose 2.23.1 or later, and a reverse proxy that serves HTTPS. No
checkout or build is needed;
[ADR-0012](decisions/adr-0012-published-images.md) explains the design.

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
URLs from them. The defaults bind to `127.0.0.1`, which suits a proxy on the
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

Each user can also export their own data from **Account** in the app
([ADR-0011](decisions/adr-0011-account-export-import.md)).

## Without plugins

Delete the `country-week` service and the two `MIMOS_PLUGINS_0_*` lines
from `compose.yml`.
