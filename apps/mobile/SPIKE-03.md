# Spike: the generated Kotlin client (item 03)

For the lead to fold into `docs/plans/android-app/03-client-and-discovery.md`;
delete this file once it is folded in.

## Outcome

openapi-generator 7.27.0 (`org.openapi.generator` Gradle plugin, pinned in
`gradle/libs.versions.toml`), generator `kotlin`, `library=multiplatform`,
generates the whole `contracts/api/openapi.yaml` (OpenAPI 3.1.0) into
`shared/build/generated/openapi` at build time, and it compiles in
`commonMain`. Android lint (warnings as errors) reads the generated
sources and finds nothing. No 3.0 down-conversion was needed, and the
`jvm-ktor` fallback was not used. The Maven build's `spring` generator stays
on 7.24.0; the two need not move together.

## What failed, and the fix

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

## Left for later items

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
