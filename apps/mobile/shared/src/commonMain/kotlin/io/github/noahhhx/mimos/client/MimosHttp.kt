package io.github.noahhhx.mimos.client

import io.github.noahhhx.mimos.api.models.Problem
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

const val REQUEST_ID_HEADER = "X-Request-Id"

/**
 * The one HTTP client for a Mimos instance's API. Every call carries its own [REQUEST_ID_HEADER]
 * and, once signed in, the bearer token; a response that is not a success throws [ApiProblem], so a
 * generated call either answers its success type or throws that.
 */
class MimosHttp(
    engine: HttpClientEngine,
    private val apiUrl: String,
    bearerToken: suspend () -> String? = { null },
) : AutoCloseable {
    private val client =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(MimosJson)
                // Every API error is problem JSON, and an operation whose only answers with a body
                // are errors (deleteRecipe) produces nothing else: accepting only JSON gets a 406.
                json(MimosJson, ContentType.Application.ProblemJson)
            }
            install(
                createClientPlugin("MimosRequest") {
                    onRequest { request, _ ->
                        request.headers.append(REQUEST_ID_HEADER, newRequestId())
                        bearerToken()?.let { request.bearerAuth(it) }
                    }
                }
            )
            HttpResponseValidator {
                validateResponse { response ->
                    if (!response.status.isSuccess()) throw response.toProblem()
                }
            }
        }

    /** A generated API, such as `api(::InstanceApi)`, calling this instance. */
    fun <T> api(create: (baseUrl: String, client: HttpClient) -> T): T = create(apiUrl, client)

    override fun close() = client.close()
}

@OptIn(ExperimentalUuidApi::class) private fun newRequestId(): String = Uuid.random().toString()

private suspend fun HttpResponse.toProblem(): ApiProblem {
    val problem = runCatching { MimosJson.decodeFromString<Problem>(bodyAsText()) }.getOrNull()
    return ApiProblem(
        status = status.value,
        title = problem?.title,
        detail = problem?.detail,
        requestId = headers[REQUEST_ID_HEADER] ?: request.headers[REQUEST_ID_HEADER],
    )
}
