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

## Done when

- The root cause is written down on this page (evidence → cause → fix).
- `harness ui create-recipe` passes.
- A regression test exists at the layer that was wrong (API endpoint test,
  contract, generated client, or web).
- A short retrospective here: what evidence was decisive, what the
  harness was missing — and the plan is updated accordingly.
