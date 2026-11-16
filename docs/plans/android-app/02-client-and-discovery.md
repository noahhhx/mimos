# 02. Generated client and instance discovery

**Depends on:** 01

The app can be pointed at any Mimos instance and call its API through a
generated client.

## Build

- `shared`: openapi-generator's Gradle plugin, generator `kotlin`,
  `library=multiplatform`, `serializationLibrary=kotlinx_serialization`,
  input `../../contracts/api/openapi.yaml`, output under `build/`, wired
  before `compileKotlin`. If the generator mishandles parts of the spec
  (`oneOf`, problem details, multipart uploads), fix it with generator
  options or templates in the repo, and record what in this file; never
  hand-write the model.
- One Ktor `HttpClient` for the app: base URL from the connected
  instance, bearer token from sign-in (item 03), an `X-Request-Id` per call
  (as `apps/web/src/lib/request-id.ts` does), problem details parsed into
  one error type the screens show.
- Backend: `GET /api/v1/public/instance` (`getInstance`, public, so
  added to the unauthenticated paths in AGENTS.md), answering `version`
  (the API's build version), `minAppVersion`, and anything else the app
  needs before sign-in that the API knows. The authorization server comes
  from `/.well-known/oauth-protected-resource`, which already exists.
  Decide here where the sign-up policy comes from: `MIMOS_SIGNUP` is a web
  setting today, so either the API learns it too, or the app offers
  Create account always and lets Keycloak's switch refuse. Record the
  choice in ADR-0029's plan, and in AGENTS.md if it adds config.
- Web: a "Use the Android app" panel (in the profile menu) showing the
  instance's API URL as text and a QR code. The QR library is a new web
  dependency; ask first, or draw the code with a small vendored encoder.
- `shared`: the connect screen. Type a URL or scan the QR code (the
  scanner arrives in item 11; until then typing only), check it answers
  `getInstance` and the resource metadata, store it, and show the server's
  version. A wrong URL, an unreachable one, and a too-old server each get
  their own message.

## Checks

- `commonTest`: discovery against a Ktor `MockEngine` for a good
  instance, a 404, a non-Mimos JSON answer, and a server below the app's
  minimum.
- `PublicInstanceEndpointTests` in `apps/api`, unauthenticated.
- `McpEndpointTests` still pins the opt-out list (decide whether
  `getInstance` is a tool).
- Gates: mobile, backend, contract, web.
