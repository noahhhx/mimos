# ADR-0012: Published images and runtime web config

- Status: Accepted
- Date: 2026-10-02
- Supersedes: the build-time URL injection in
  [ADR-0004](adr-0004-frontend-auth-in-the-browser.md)

## Context

Mimos is about to run on a home server. `deploy/docker` boots the whole
product, but only by building every image from a checkout, and only as a
development stack: Keycloak runs `start-dev` with no persistent storage,
the realm export imports two test users with known passwords, and its
`mimos-web` client only accepts redirects to `http://localhost:3000`.

Prebuilt images would let a server run Mimos with no source or build
toolchain. One thing stopped that: the web image inlined its public URLs
(`NEXT_PUBLIC_*` build args, ADR-0004), so an image built anywhere else
pointed at that machine's `localhost`.

## Decision

- **The web app reads its public URLs at run time.** The container's env
  (`MIMOS_API_URL`, `MIMOS_OIDC_AUTHORITY`, `MIMOS_OIDC_CLIENT_ID`,
  `MIMOS_APP_URL`) is served as `/runtime-config.js`, a dynamic route that
  the root layout loads with `beforeInteractive`, so Next runs it before
  any app code. `src/lib/config.ts` reads that global in the browser and
  the env on the server. No `NEXT_PUBLIC_*` values remain. Static pages
  stay static, because the URLs are in a separate script and never in
  their HTML.
- **The realm takes its web origin from env.** `mimos-realm.json` uses
  `${MIMOS_WEB_ORIGIN:http://localhost:3000}`, which Keycloak resolves at
  import. The default keeps the dev stack and the API integration tests
  unchanged.
- **The Keycloak image carries the realm, minus its users.** A build stage
  strips `users` from the export and bakes the result into the image's
  import directory. The dev compose stack mounts the full export over it,
  so `test`/`test2` exist only there.
- **CI publishes four images to GHCR**: `mimos-api`, `mimos-web`,
  `mimos-keycloak`, `mimos-country-week`. They are published only after
  every other CI job passes on a push. `main` publishes `main` and
  `sha-<commit>`. A tag `v1.2.3` publishes `1.2.3`, `1.2` and `latest`.
  Images are amd64 only.
- **`deploy/selfhost` runs the published images in production mode.** The
  compose file runs Keycloak with `start`, keeps its data in its own
  database on the same Postgres, sits behind the operator's reverse proxy
  (`KC_PROXY_HEADERS=xforwarded`), binds ports to loopback by default, and
  restarts services. It requires secrets and URLs instead of defaulting
  them. TLS is the operator's: sign-in needs a secure context (PKCE uses
  Web Crypto), so anything but `localhost` must be HTTPS.

## Consequences

- Retargeting the web app means restarting its container, not rebuilding
  it. The dev compose stack passes the same variables.
- `deploy/docker` stays the build-from-source and CI parity stack;
  `deploy/selfhost` is what a server runs. CI validates the self-host
  compose file but does not boot it. The images it runs are the ones the
  compose job's code built.
- The realm is imported once. After that it lives in Keycloak's database,
  so a later realm change (or a new `WEB_ORIGIN`) is made in the admin
  console, or by dropping the realm and restarting.
- While the repository is private, so are its packages, and a server
  needs `docker login ghcr.io` with a token that can read packages.
- arm64 images, a Helm chart, or a bundled reverse proxy can come later if
  needed. None of them changes this design.
