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
  an `http://` address other than `localhost` (Mimos needs `https://`,
  with a link to the self-hosting guide's "Why HTTPS"; ADR-0030), and a
  server too old or an app too old each get their own message.

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

## Done

Landed on `docs/mobile-app-plan`. `getInstance` answers
the server's Maven version without its qualifier (`0.1.0` today) and
`minAppVersion` `0.0.0`. Its tag is `instance`, not `public`: the spring
generator makes one interface per tag, and `PublicRecipesController`
implements all of `PublicApi`. Verified on the emulator against the
compose stack: `http://example.com` gets the HTTPS message and its link,
and `http://localhost:3000` connects and shows server version 0.1.0, and
the connection survives a restart of the app.

### The spike

openapi-generator 7.27.0 (`org.openapi.generator` Gradle plugin, pinned in
`gradle/libs.versions.toml`), generator `kotlin`, `library=multiplatform`,
generates the whole `contracts/api/openapi.yaml` (OpenAPI 3.1.0) into
`shared/build/generated/openapi` at build time, and it compiles in
`commonMain`. Android lint (warnings as errors) reads the generated
sources and finds nothing. No 3.0 down-conversion was needed, and the
`jvm-ktor` fallback was not used. The Maven build's `spring` generator stays
on 7.24.0; the two need not move together.

### What failed, and the fix

1. **`dateLibrary`.** The multiplatform library refuses the default date
   library ("Multiplatform only supports string and kotlinx-datetime").
   Fix: `dateLibrary=kotlinx-datetime`. Dates are `kotlinx.datetime.LocalDate`
   and date-times `kotlin.time.Instant`, with kotlinx-datetime 0.8.0.
2. **`serializationLibrary=kotlinx_serialization` doubles annotations.**
   `library=multiplatform` already emits kotlinx.serialization, so the option
   writes `@Serializable@Serializable` on every model (110 compile errors).
   Fix: leave the option out. The library uses kotlinx.serialization anyway.
3. **The free-form `importAccount` body.** Its schema is a bare
   `type: object`. The 3.1 parser types the parameter `String`, while the
   multiplatform template wraps map bodies in a `Map<String, String>`
   serializer, so it does not compile. Fix: `typeMappings` `object` to
   `JsonObject`, with an `importMappings` entry for
   `kotlinx.serialization.json.JsonObject`, and one overridden partial,
   `shared/openapi-templates/libraries/multiplatform/serial_wrapper_request_map.mustache`.
   The override types the wrapper by the parameter's own type instead of
   `Map<String, baseType>`, so the call sends the document as given.
4. **Unknown enum values.** `enumUnknownDefaultCase=true` adds a last case,
   `unknown_default_open_api`, to every enum. Under `library=multiplatform`,
   though, a string enum gets a plain `@Serializable`, so an unknown value
   still throws. The fallback serializer exists only on the
   non-multiplatform `kotlinx_serialization` path, which item 2 rules out.
   Fix: an overridden `enum_class.mustache` (multiplatform only, 34 lines)
   whose serializer reads an unknown value as `unknown_default_open_api`.
   `inlineSchemaOptions` `RESOLVE_INLINE_ENUMS=true` makes the one inline
   enum (`AccountExport.format`) a model of its own, `AccountExportFormat`,
   so it goes through the same template.
5. **`PanelBlock`.** The generator's `oneOf` with a discriminator comes out
   as an abstract sealed class with no subclasses, and it cannot decode
   anything. `schemaMappings` points `PanelBlock` at the hand-written
   `io.github.noahhhx.mimos.client.PanelBlock`. That class has a case per
   known `type`, each wrapping the generated block model, plus `Unknown`.

The Gradle plugin's newer DSL takes `inputSpec` and `outputDir` as file
properties, not strings. `cleanupOutput = true` deletes the generated files
of a schema the spec drops. Tests and docs generation are off.

### Left for later items

- **Binary responses (item 07).** `getRecipePicture` returns
  `HttpResponse<OctetByteArray>`. Its `body()` throws
  `NoTransformationFoundException`, because `OctetByteArray` is a JSON
  (hex) serializer and the response is an image. Read the bytes with
  `.response.readRawBytes()` instead. A MockEngine run shows that path
  returns the bytes.
- **Multipart upload (item 07).** `addRecipePicture(recipeId, file:
  FormPart<InputProvider>)` compiles. It has not been run against a
  server.
- **Path parameters.** The template escapes each path segment with
  `encodeURLQueryComponent`, which writes a space as `+`. The spec's path
  parameters are UUIDs, slugs, barcodes, dates, plugin ids, and invite
  tokens, none of which hold a space. This is inferred from their schemas,
  not tested.
- **Generated warnings.** Kotlin reports 9 "unnecessary safe call"
  warnings in the generated APIs. Warnings do not fail the Kotlin compile,
  and lint does not flag them.

## Review fixes

An independent review of the first build changed four things:

1. `http://` is allowed only for `localhost`, and only in a debuggable
   build (`allowLocalhostHttp`, from `FLAG_DEBUGGABLE`), matching the
   network security configs. `127.0.0.1` is refused, and a release build
   refuses every `http://` address, so no address passes the check and
   then fails as a generic network error.
2. The "server too old" message names no version while the minimum is
   0.0.0 or the server did not say one.
3. A runtime config whose `apiUrl` is `http://` is `InsecureServer`, with
   copy that blames the server's setup, not the address typed.
4. The contract checker matches parameters by name and `in` and
   `oneOf`/`anyOf`/`allOf` members by `$ref`, and also reports a removed
   parameter, response status code, or union member, a property dropped
   from `required`, and a changed `type`. It still misses a swapped `$ref`
   and a new required request field.
