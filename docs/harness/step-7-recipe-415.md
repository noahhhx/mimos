# Step 7 — First use: the recipe 415

**Status:** done · [Back to the plan](index.md)

## The bug

On the locally deployed stack, creating a recipe from the web app fails
with `415 Unsupported Media Type`. The cause is deliberately **not**
guessed here — finding it is the harness's acceptance test.

## Goal

Diagnose and fix the 415 using only the harness's evidence, and leave
behind a regression test at the right layer plus the scenario that
reproduced it.

## Approach

Each step of the harness should narrow this bug; note what each one
showed, so the harness's usefulness is judged on a real case.

1. **Step 1** — `harness api POST /api/v1/recipes` with a well-formed JSON
   body. If it succeeds, the server accepts JSON and the difference lies in
   what the browser sends; if it fails, the server side is implicated.
2. **Step 2** — `harness ui create-recipe` and the HAR in `summary.md`:
   the exact request the browser sent (method, URL, `Content-Type`, body)
   and the problem-details response.
3. **Step 3** — the API's error log line for that request ID: the resolved
   exception and the media type it rejected.
4. **Step 4** — `diag` mappings: what `POST /api/v1/recipes` declares it
   consumes; `loglevel org.springframework.web DEBUG` for converter
   selection.
5. **Step 5** — only if still unclear: a breakpoint in the handler (or a
   "not hit" result proving the request is rejected before it).

The fix may be useful before all steps are built — if the evidence from an
early step is conclusive, fix it then and keep this page as the record.

## Evidence log

- **Step 1 (2026-10-01).** `harness api POST /api/v1/recipes` with a
  well-formed JSON body (`Content-Type: application/json`) as `test` →
  **201 Created**. The server accepts JSON, so the difference lies in what
  the browser sends — step 2's HAR should show it. For comparison, the same
  body with `-H 'Content-Type: text/plain'` → 415, with the API logging
  `DefaultHandlerExceptionResolver : Resolved
  [HttpMediaTypeNotSupportedException: Content-Type 'text/plain' is not
  supported]` at WARN. Side finding: that 415's body is Spring's default
  error JSON (`application/json`, `timestamp`/`status`/`error`/`path`),
  not RFC 9457 problem-details as AGENTS.md requires. Errors Spring
  resolves itself bypass `ApiExceptionHandler` (step 3 already notes this
  for logging).
- **Step 2 (2026-10-01).** `harness ui create-recipe` reproduces it: the
  form shows "The API rejected the recipe." and the HAR has `POST
  /api/v1/recipes` → 415 **with no `Content-Type` header at all** — the
  browser sent `Content-Length: 315` and `Authorization`, but no media
  type, so Spring rejects it (`HttpMediaTypeNotSupportedException:
  Content-Type is not supported`, WARN). Chromium did not expose the 315
  body bytes to the HAR. The `harness api` 201 differs from this request
  in the `Content-Type` header, so the question moves to the web side:
  how the request is built before `fetch` sends it.
- **Step 3 (2026-10-01).** On the debug stack, `harness ui create-recipe`
  lists the 415's request ID, and its log lines show the request reached
  the API with no `Content-Type` and was rejected before any handler ran:
  `POST /api/v1/recipes -> 415 (8 ms) content-type=<none> accept=*/*
  exception=HttpMediaTypeNotSupportedException: Content-Type is not
  supported`, alongside Spring's `DefaultHandlerExceptionResolver` WARN.
  This confirms step 2 from the server side and adds nothing new about the
  cause, which lies in what the browser sends.
- **Step 4 (2026-10-01).** `harness diag`'s `routes.txt` has exactly one
  handler for `POST /api/v1/recipes`, and it declares `consumes=application/json`.
  With `harness loglevel org.springframework.web DEBUG`, the failing
  request logs `DispatcherServlet: POST "/api/v1/recipes"`, then the 415,
  with **no `Mapped to …` line**. Handler mapping found no match, because
  a request without a `Content-Type` fails the `consumes` condition. No
  handler or message converter ever ran. So the server is behaving as
  declared, and the fix belongs where the browser request is built (or,
  if bodies without a media type should be accepted, in the contract).
- **Step 5 (2026-10-01).** `harness debug break
  RecipesController.createRecipe --then "ui create-recipe"` → **not hit**:
  the handler never runs for the browser's request. The same breakpoint
  with `--then "api POST /api/v1/recipes --body recipe.json"` is hit, with
  `recipeInput.getTitle()` the posted title. This is the plan's predicted
  "not hit" and agrees with step 4. It adds no new cause, but it proves
  the rejection happens before the handler, without reading framework
  logs.
- **Cause (2026-10-01).** With the server cleared, the remaining question
  was how the browser builds the request, and no harness command sees
  inside the page's JavaScript, so this was read from the code. The
  generated client (`libraries/api-client/src/client/client/client.gen.ts`)
  builds a complete `Request`, with `Content-Type: application/json` and
  the body, and calls the configured fetch as `fetch(request)`, with no
  `init`. The web app's fetch (`apps/web/src/lib/api.ts`) built its headers
  from `init?.headers` (undefined), added `Authorization` and
  `X-Request-Id`, and called `fetch(input, { ...init, headers })`. Headers
  in `init` **replace** a Request's own, so the body survived (the HAR's
  `Content-Length: 315`) but the media type did not. This matches every
  piece of evidence above. GETs carry no body, which is why only writes
  failed. The bug dates from the frontend skeleton (`9ad4587`), so every web
  call with a body (recipes, plan entries, logs, shopping list) went out
  without a media type. `create-recipe` is the only scenario that writes,
  so nothing else caught it.

## Fix

- **Layer:** web. The API behaves as its contract declares (`consumes:
  application/json`), and the generated client sends the header. The web
  wrapper drops it.
- **Change:** the wrapper moved to `apps/web/src/lib/api-fetch.ts`
  (`authorizedFetch`). It starts from `init`'s headers when there are any,
  otherwise from the `Request`'s, which is what `fetch` itself does. Then
  it adds the token and request ID. `api.ts` only wires it to
  `userManager`.
- **Regression test:** `apps/web/test/api-fetch.test.ts` calls it the way
  the generated client does, with one prepared `Request`, and asserts that
  the outgoing request keeps its `Content-Type` and body. With the old
  header line, that test fails (`Content-Type` absent), and so does the one
  that keeps a caller's `X-Request-Id`. These are the web app's first unit
  tests. They run on `node --test` (`npm test -w @mimos/web`, in CI's
  TypeScript job), like the plugin and harness tests, with no new
  dependency.
- **Scenario:** `harness ui create-recipe` passes on a rebuilt stack: `POST
  /api/v1/recipes` → 201 with `Sent: application/json`, and the page lands
  on the new recipe. It is out of CI's `KNOWN_FAILING`, so it now runs as a
  regression test.

## Retrospective

- **Decisive evidence:** step 2. The HAR summary's "Sent" column, **no
  Content-Type**, against `harness api`'s 201 (step 1), placed the bug in
  the browser before any server-side digging. Steps 3–5 each confirmed the
  rejection happened before a handler ran. They were useful for confidence
  and cheap to run, but they added nothing on the cause. On a server-side
  bug they would have been the decisive steps.
- **What the harness was missing:** a view inside the page. Once the
  evidence said "the browser builds the request wrong", the harness had
  nothing more to offer, and the last hop (client → wrapper → `fetch`)
  came from reading code. The Node inspector (step 5) covers the Next
  server, not browser code. A candidate addition: a scenario option that
  wraps `window.fetch` with an init script and records each call's
  arguments (input type, `init` keys, headers before and after) into the
  run folder. Do not build it until a second bug needs it.
- **What the web app was missing:** a unit-test runner. Its only tests were
  scenarios, so the fix's regression test also needed the runner (added
  here, see above).
- **Scenario coverage:** one write scenario hid a bug that affected every
  write. Scenarios for the plan, log, and shopping-list flows would have
  caught it on day one; they are worth adding as those flows change.
- **Found along the way, fixed as follow-ups:**
  - **Problem-details for Spring's own errors.** 415s, 405s, 404s for
    unknown paths, 406s, and unexpected exceptions returned Spring's
    default error JSON (`timestamp`/`status`/`error`/`path`) or an empty
    body, not the RFC 9457 problem-details that the contract promises (step
    1's side finding; reproduced with `harness api` for 415, 405, 404, and
    406). `ApiExceptionHandler` now extends Spring's
    `ResponseEntityExceptionHandler`, which renders all of Spring MVC's
    errors as problem-details. Anything unmapped is a 500 `Internal error`
    problem whose message is never sent and whose stack trace is logged
    with the request ID. `ProblemDetailsEndpointTests` covers each kind;
    `harness api` shows `application/problem+json` for all five.
  - **A false "container on older image" in `harness status`.** Right
    after `harness up`, status flagged `country-week`, and this page first
    blamed compose for not recreating it. That was wrong. On the containerd
    image store, `docker image inspect` and `compose images` report the
    image **index** digest, and a rebuild can re-wrap an unchanged platform
    manifest in a new index. The container's `com.docker.compose.image`
    label, which compose compares to decide on recreation, named the
    current build's manifest (`docker image inspect --platform
    linux/amd64` → the same digest), so compose was right to leave it
    running. Status now compares that label with the image inspected for
    the daemon's platform, the same check compose makes. A container built
    from an older image is still flagged (checked by building `api` without
    recreating it), and `harness up` recreated it and cleared the flag.

## Done when

- The root cause is written down on this page (evidence → cause → fix).
- `harness ui create-recipe` passes, and `create-recipe` is removed from
  `KNOWN_FAILING` in `.github/workflows/ci.yml` (CI fails until it is), so
  the scenario runs as a regression test.
- A regression test exists at the layer that was wrong (API endpoint test,
  contract, generated client, or web).
- A short retrospective here: what evidence was decisive, what the
  harness was missing — and the plan is updated accordingly.
