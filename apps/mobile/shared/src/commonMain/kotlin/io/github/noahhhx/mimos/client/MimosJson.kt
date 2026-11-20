package io.github.noahhhx.mimos.client

import kotlinx.serialization.json.Json

/**
 * How the app reads and writes the API's JSON. A self-hosted server can be newer than the app, so
 * fields the app does not know are ignored; unknown enum values read as each generated enum's
 * `unknown_default_open_api` case (the generator's templates in `shared/openapi-templates`).
 */
val MimosJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
