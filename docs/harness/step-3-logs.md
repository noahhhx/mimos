# Step 3 — Logs and request correlation

**Status:** planned · [Back to the plan](index.md)

## Goal

Given a failing request seen in the browser, an agent can jump straight to
every log line that request produced in the web server, the API, and any
plugin it called — and every 4xx/5xx the API returns leaves a log line
explaining why.

## Prerequisites

Steps 1 and 2.

## Current gaps

- Nothing ties a browser request to an API log line.
- `ApiExceptionHandler` maps exceptions to problem-details but logs
  nothing; exceptions Spring resolves itself (e.g.
  `HttpMediaTypeNotSupportedException` → 415) never reach it, and 401/403s
  are produced inside the security chain.
- API logs are human-formatted text, which is awkward to filter.

## Design

### Request IDs (product change — useful beyond the harness)

- **API:** a servlet filter ordered before the security chain reads
  `X-Request-Id`; if absent or malformed (not a bounded token of safe
  characters) it generates one (ID source injected, per the "randomness is
  injected" convention). The ID goes into the MDC as `requestId` for the
  duration of the request and is echoed as a response header.
- **CORS:** `SecurityConfig` currently allows only `Authorization` and
  `Content-Type` request headers; add `X-Request-Id` to the allowed headers
  and expose it as a response header, or browsers will drop it.
- **Web:** the fetch wrapper in `apps/web/src/lib/api.ts` sets a fresh
  `X-Request-Id` per call; `apps/web/src/lib/server-api.ts` does the same
  for server-side fetches.
- **Plugins (optional in this step):** the plugin runtime forwards the ID
  on outbound extension-API calls. If done, the header is documented in
  `contracts/plugins/` as optional — an additive contract change.

### Request and error logging (product change)

- One access-log line per API request: method, path, status, duration
  (injected clock), request `Content-Type`/`Accept`, request ID. Covers
  security-chain responses because the filter wraps the chain.
- Every 4xx/5xx is logged once with the resolved exception class and
  message. Evaluate Spring Boot's `spring.mvc.log-resolved-exception`
  against an explicit resolver/filter — pick whichever also covers the
  `ApiExceptionHandler` paths and the security chain; record the choice
  here.

### The debug overlay (introduced here)

`deploy/docker/compose.debug.yml` is created in this step, used via
`harness up --debug`:

- API: `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` — JSON lines with the MDC
  `requestId` as a field. The default stack keeps human-readable logs.
- Later steps add actuator exposure (step 4) and debug ports (step 5).

The overlay is plain compose, so humans without Nix can use it too
(`docker compose -f compose.yml -f compose.debug.yml up`).

### Harness

- `harness logs --request-id <id>` filters captured logs across services
  (JSON field match for the API in debug mode, substring match otherwise).
- `harness ui` and `harness api` record each request's ID; `summary.md`'s
  failing-request table gains a request-ID column and the matching API log
  lines inline.

## Changes

- `apps/api` — request-ID filter, access/error logging, CORS headers,
  tests (`RequestIdFilter` behavior; an endpoint test asserting the header
  is echoed and generated; a test that a 415 produces an error log line).
- `apps/web` — request IDs in both fetch paths.
- `deploy/docker/compose.debug.yml`.
- `tools/harness` — `logs --request-id`, summary integration.
- Optional: `integrations/plugins` + `contracts/plugins` header forwarding.
- Docs: AGENTS.md (logging conventions; the debug overlay).

## Done when

- Every API response carries `X-Request-Id`; a browser-supplied ID is
  echoed; a malformed one is replaced.
- `harness ui create-recipe` (debug stack) lists the failing request's ID,
  and `harness logs --request-id <id>` shows the API's access line and the
  resolved exception.
- `./mvnw -B verify` and the web typecheck/build pass; the default compose
  stack is unchanged apart from the new header and log lines.
