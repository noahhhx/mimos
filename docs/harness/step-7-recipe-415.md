# Step 7 — First use: the recipe 415

**Status:** planned · [Back to the plan](index.md)

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

## Done when

- The root cause is written down on this page (evidence → cause → fix).
- `harness ui create-recipe` passes.
- A regression test exists at the layer that was wrong (API endpoint test,
  contract, generated client, or web).
- A short retrospective here: what evidence was decisive, what the
  harness was missing — and the plan is updated accordingly.
