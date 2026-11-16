# 03. Generated client and instance discovery

**Depends on:** 01

The app can be pointed at any Mimos instance by its web address and call
its API through a generated client.

## Build

- A spike first: generate and compile the whole `openapi.yaml` (OpenAPI
  3.1.0, with multipart uploads and binary responses) with
  openapi-generator's `kotlin` generator, `library=multiplatform`,
  `serializationLibrary=kotlinx_serialization`,
  `hideGenerationTimestamp=true`. 3.1 support in the generator is beta.
  Record here what failed and the fix (generator options, templates in the
  repo, or a 3.0 down-conversion of the spec at build time). If the
  multiplatform library cannot be made to work, the fallback is the
  `jvm-ktor` library in `androidMain`, accepting that iOS would need the
  client moved later. Take that to the owner before using it.
- `shared`: the generator's Gradle plugin, input
  `../../contracts/api/openapi.yaml`, output under `build/`, before
  `compileKotlin`. Generated at build time like the server stubs, not
  committed like `@mimos/api-client`: a Kotlin client has no consumer
  outside this build. AGENTS.md's API-style row says so.
- Tolerant reading, because a self-hosted server can be newer than the
  app: the JSON reader ignores unknown keys and reads an unknown enum
  value as an `Unknown` case. `PanelBlock` gets a hand-written
  serializer with an `Unknown` case, the one model not generated; it is
  listed here and in AGENTS.md as the exception.
- One Ktor `HttpClient`: base URL from the connected instance, bearer
  token from sign-in (item 04), an `X-Request-Id` per call (as
  `apps/web/src/lib/request-id.ts`), problem details parsed into one error
  type the screens show.
- Discovery from the web address people already know:
  - Web: `/runtime-config.json`, a JSON twin of `/runtime-config.js`
    from the same `src/lib/config.ts` values (API URL, OIDC authority and
    client id, app URL, sign-up policy).
  - Backend: `GET /api/v1/public/instance` (`getInstance`, public, so
    added to AGENTS.md's unauthenticated paths), answering `version` and
    `minAppVersion` (a constant in `apps/api`, raised only by a change an
    older app cannot survive; with the additive-only rule below that should
    be rare). Making `version` the release's version is item 16.
- Contract rule, in ADR-0029's consequences and AGENTS.md: `/api/v1`
  changes are additive only from the first app release. A CI step diffs
  `openapi.yaml` against the last `v*` tag's (once a tag exists) and
  fails on removed operations, fields, or enum values.
- `shared`: the connect screen. Type the web address, fetch
  `runtime-config.json`, then `getInstance`, store the instance, show
  its version. A wrong URL, a site that is not Mimos, an unreachable one,
  and a server too old or an app too old each get their own message.

## Checks

- `commonTest`: discovery against a Ktor `MockEngine` for a good
  instance, a 404, a non-Mimos JSON answer, and each version outcome; an
  unknown field and an unknown enum value decode.
- `PublicInstanceEndpointTests` in `apps/api`, unauthenticated.
- Web: a unit test of the `runtime-config.json` payload.
- `McpEndpointTests` still pins the opt-out list (`getInstance` stays a
  tool unless there is a reason).
- On the emulator (item 02): connect to `http://localhost:3000`.
- Gates: mobile, backend, contract, web.
