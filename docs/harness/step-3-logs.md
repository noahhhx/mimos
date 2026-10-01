# Step 3 — Logs and request correlation

**Status:** done (2026-10-01) · [Back to the plan](index.md)

## Goal

Given a failing request seen in the browser, an agent can jump straight to
every log line that request produced in the web server and the API — and
every 4xx/5xx the API returns leaves a log line explaining why.

## Using it

```sh
devenv shell -- harness up --debug            # JSON API logs (optional — text logs correlate too)
devenv shell -- harness ui create-recipe      # the summary lists each API call's request ID
devenv shell -- harness logs --request-id <id>
devenv shell -- harness api GET /api/v1/me    # sends a fresh X-Request-Id; the summary shows it
cat .harness/runs/latest/summary.md
```

| Command | What it does |
| --- | --- |
| `harness logs --request-id <id> [--service <s>]... [--since <t>]` | Captures the logs as usual, then writes `logs/request-<id>.log` and prints every line that mentions the request, across services (`<service> \| <line>`). Exit code 1 when nothing matches. |
| `harness up --debug` | Adds `deploy/docker/compose.debug.yml`: the API logs JSON lines (Elastic Common Schema) instead of text. |

What changed in the existing commands:

- **`harness api`** sends `X-Request-Id: <uuid>` (`-H` replaces it; `-H
  'X-Request-Id:'` drops it). `api/exchanges.jsonl` records the ID the API
  echoed as `requestId`, and `summary.md` shows it. A failed call's log
  block is now the lines carrying its ID, across services; only when none
  do (say, an API image from before this step) does it fall back to the
  API lines since the request was sent.
- **`harness ui`** — the summary's API call table gains a **Request ID**
  column, and each failed request shows its ID and the log lines it
  produced, inline. The "API log lines since the scenario started" block
  remains as a fallback when no failed request's lines were found.

Matching: a JSON line matches when its `requestId` field equals the ID; a
text line matches when it contains it. JSON lines are shown in summaries
as `<time> <LEVEL> <Logger>: <message> [<error>]`; the files under `logs/`
keep them raw.

### The request ID

- **API:** `RequestLoggingFilter` (`apps/api`, `api.support`) runs before
  the security chain. It takes the `X-Request-Id` request header when it is
  1–64 characters of `A-Z a-z 0-9 . _ -`, and generates a UUID otherwise;
  echoes it as a response header; and puts it in the MDC as `requestId`
  while the request runs. `logging.pattern.correlation` prints it on every
  text line logged during the request (`[<id>]` after the thread name);
  structured formats get it as a `requestId` field. Documented in the
  OpenAPI `info.description`.
- **CORS:** `X-Request-Id` is an allowed request header and an exposed
  response header.
- **Browser:** the `apiClient` fetch wrapper (`apps/web/src/lib/api.ts`)
  sends a fresh ID per call.
- **Web server:** `publicApi` (`apps/web/src/lib/server-api.ts`) sends one
  on uncached fetches only (see design notes), and logs every failed call
  as `[mimos-api] <METHOD> <url> -> <status> request-id=<id>`, with the ID
  the API echoed.

### The access line

One line per API request, from `RequestLoggingFilter`:

```
POST /api/v1/recipes -> 415 (8 ms) content-type=<none> accept=*/* exception=HttpMediaTypeNotSupportedException: Content-Type is not supported
```

INFO below 400, WARN for 4xx, ERROR for 5xx; successful
`/actuator/health` probes log at DEBUG, so compose's healthchecks stay out
of the log. With ECS, the details are also fields: `method`, `path`,
`status`, `durationMs`, `contentType`, `accept`, `exception`.

## Design notes

Decisions made while building, beyond the plan:

- **The error is part of the access line, not a separate line.** The plan
  asked to evaluate `spring.mvc.log-resolved-exception` against an explicit
  filter. That property only affects Spring MVC's exception resolvers, so it
  misses 401/403s from the security chain and exceptions that escape the
  chain. The filter instead names the exception on the 4xx/5xx access line,
  taken from (in order) the security chain (`ProblemDetailSecurityHandlers`
  calls `RequestLoggingFilter.recordFailure`), an exception escaping the
  chain (logged as a 500, then rethrown), or the exception Spring MVC
  resolved (`DispatcherServlet.EXCEPTION_ATTRIBUTE`, set for both
  `ApiExceptionHandler` and Spring's own resolvers). Since step 7,
  `ApiExceptionHandler` resolves Spring MVC's own errors too, which log no
  line of their own (405s aside, via Spring's `PageNotFound` WARN), so the
  access line is their record; unexpected exceptions get an ERROR line with
  the stack trace. Both carry the request ID.
- **One filter, `RequestLoggingFilter`,** rather than a separate
  `RequestIdFilter` plus an access logger: the access line needs the ID in
  the MDC and has to wrap the same chain. Registered at
  `HIGHEST_PRECEDENCE + 10` (the security chain is at -100). The ID source
  (`Supplier<String>`) and the `Clock` that times the request are injected.
- **The ECS key is `exception`, not `error`.** In ECS, `error` is an
  object (`error.type`, `error.message`, `error.stack_trace`), so a string
  `error` field would clash with it.
- **Exception messages are kept on one line**: CR/LF become `\r`/`\n`,
  since a message can quote request content.
- **Cached server-side fetches send no ID.** Next.js's fetch cache keys on
  request headers, so a fresh ID per call would make every cached library
  fetch a miss and defeat ISR. Those calls get an ID from the API, and the
  web server logs that one when a call fails. Search fetches (`no-store`)
  send their own.
- **`crypto.randomUUID` has a fallback.** It exists only in secure
  contexts, and a self-hosted instance served over plain http on a LAN
  address is not one; `newRequestId()` then uses `getRandomValues`.
- **Plugin forwarding is deferred.** The plan made it optional. The
  reference plugin logs nothing per request, so forwarding the ID would
  only show up in the contract. It belongs with the first plugin that logs
  requests: an optional `X-Request-Id` header in `contracts/plugins/` (an
  additive change) and forwarding in `PluginClient`.

## Changes

- `apps/api` — `RequestLoggingFilter` and `RequestLoggingConfig`
  (`api.support`); `SecurityConfig` (CORS allowed/exposed header);
  `ProblemDetailSecurityHandlers` (records the failure);
  `logging.pattern.correlation` in `application.yml`; tests.
- `contracts/api/openapi.yaml` — `X-Request-Id` in `info.description`
  (the regenerated client is unchanged).
- `apps/web` — `src/lib/request-id.ts`; request IDs in `api.ts` and
  `server-api.ts`; failure logging in `server-api.ts`.
- `deploy/docker/compose.debug.yml` — new: ECS logs for the API.
- `tools/harness` — `src/logs.ts` (matching, ECS formatting);
  `logs --request-id`; request IDs in `api` and in the `ui` summary
  (`src/har.ts`); tests.
- AGENTS.md — logging conventions, the debug overlay.

## Tests

- `RequestLoggingFilterTest` (unit): ID generated, echoed, or replaced when
  malformed (empty, spaces, line breaks, markup, `;`, 65 characters); MDC
  scoped to the request; the access line's text, level, and key-values; the
  exception from Spring MVC, from the security chain, and one escaping the
  chain (logged as a 500 and rethrown); health probes at DEBUG.
- `RequestLoggingEndpointTests` (Testcontainers Postgres + Keycloak):
  every response has an ID; a supplied one is echoed and a malformed one
  replaced; a 415 leaves a WARN line with the request ID and
  `HttpMediaTypeNotSupportedException`; an anonymous 401 leaves one with
  `InsufficientAuthenticationException`; a CORS preflight allows
  `X-Request-Id` and responses expose it.
- `npm test -w @mimos/harness`: log-line parsing, request-ID matching
  (JSON field equality, text substring), ECS formatting (nested and flat
  field names), grouping across services, `--request-id` validation,
  request-ID headers in `harness api`, and the HAR summary's request IDs
  (the API's echo wins over what was sent) and per-request log lines.

## Verification (2026-10-01)

- `harness up --debug` → healthy; the API logs ECS JSON with `requestId`
  and the access-line fields.
- `harness ui create-recipe` (still failing, as expected until step 7):
  the API call table lists each call's request ID, and the failed `POST
  /api/v1/recipes` → 415 shows its ID and, inline, the API's
  `DefaultHandlerExceptionResolver` WARN and the access line:
  `POST /api/v1/recipes -> 415 (8 ms) content-type=<none> accept=*/*
  exception=HttpMediaTypeNotSupportedException: Content-Type is not
  supported`.
- `harness logs --request-id <that id>` prints the same two lines; an
  unknown ID prints nothing and exits 1.
- `harness api POST /api/v1/recipes -H 'Content-Type: text/plain'` →
  415; the summary shows the request ID and the two lines carrying it.
- `GET http://localhost:3000/recipes/<no such slug>` → 404; `harness logs
  --request-id` finds the API's access line
  (`NoSuchElementException: no library recipe with slug: …`) and the web
  server's `[mimos-api] … -> 404 request-id=…` lines together. (The web
  line appears twice: the page and its metadata each ask for the recipe,
  and Next.js answers both from one fetch.)
- Without `--debug`, the default stack logs text lines with `[<id>]` in
  them, and the same commands find them.
