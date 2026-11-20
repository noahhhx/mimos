package io.github.noahhhx.mimos.client

import io.github.noahhhx.mimos.api.apis.FuelApi
import io.github.noahhhx.mimos.api.apis.MeApi
import io.github.noahhhx.mimos.api.apis.PluginsApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.FuelGoal
import io.github.noahhhx.mimos.api.models.FuelSex
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive

private const val API = "https://api.mimos.example.com"

private val me =
    """{"id":"p1","subjectId":"s1","displayName":"Test","createdAt":"2026-11-17T10:00:00Z"}"""

private fun answering(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    vararg headers: Pair<String, String>,
) = MockEngine { _ ->
    respond(
        body,
        status,
        headersOf(
            HttpHeaders.ContentType to listOf("application/json"),
            *headers.map { it.first to listOf(it.second) }.toTypedArray(),
        ),
    )
}

class MimosHttpTest {
    @Test
    fun `every call carries its own request id and the bearer token`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(me, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = MimosHttp(engine, API) { "token-1" }.api(::MeApi)
        api.getMe()
        api.getMe()
        val ids = requests.map { it.headers[REQUEST_ID_HEADER] }
        assertTrue(ids.all { it != null && Regex("[0-9a-f-]{36}").matches(it) }, "$ids")
        assertNotEquals(ids[0], ids[1])
        assertEquals("Bearer token-1", requests[0].headers[HttpHeaders.Authorization])
        assertEquals("$API/api/v1/me", requests[0].url.toString())
    }

    @Test
    fun `no token means no Authorization header`() = runTest {
        var authorization: String? = "unset"
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond(me, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        MimosHttp(engine, API).api(::MeApi).getMe()
        assertNull(authorization)
    }

    @Test
    fun `problem details become an ApiProblem with the server's request id`() = runTest {
        val engine =
            answering(
                """{"type":"about:blank","title":"Conflict","status":409,"detail":"The account is not empty."}""",
                HttpStatusCode.Conflict,
                REQUEST_ID_HEADER to "req-42",
            )
        val problem = assertFailsWith<ApiProblem> { MimosHttp(engine, API).api(::MeApi).getMe() }
        assertEquals(409, problem.status)
        assertEquals("Conflict", problem.title)
        assertEquals("The account is not empty.", problem.detail)
        assertEquals("req-42", problem.requestId)
    }

    @Test
    fun `an error without problem details is an ApiProblem with the call's own request id`() =
        runTest {
            var sent: String? = null
            val engine = MockEngine { request ->
                sent = request.headers[REQUEST_ID_HEADER]
                respond(
                    "<h1>Bad gateway</h1>",
                    HttpStatusCode.BadGateway,
                    headersOf(HttpHeaders.ContentType, "text/html"),
                )
            }
            val problem =
                assertFailsWith<ApiProblem> { MimosHttp(engine, API).api(::MeApi).getMe() }
            assertEquals(502, problem.status)
            assertNull(problem.title)
            assertEquals(sent, problem.requestId)
        }

    @Test
    fun `unknown fields are ignored and unknown enum values read as unknown`() = runTest {
        val engine =
            answering(
                """{"enabled":true,"sex":"NONBINARY","goal":"GAIN","proteinGPerKg":1.8,"addedLater":{"x":1}}"""
            )
        val profile = MimosHttp(engine, API).api(::FuelApi).getMyFuelProfile().body()
        assertEquals(FuelSex.unknown_default_open_api, profile.sex)
        assertEquals(FuelGoal.GAIN, profile.goal)
    }

    @Test
    fun `panel blocks read as their kind, and an unknown kind as Unknown`() = runTest {
        val engine =
            answering(
                """
                {"panels":[{"pluginId":"country-week","pluginName":"Country of the Week","blocks":[
                  {"type":"text","text":"Spin for a country."},
                  {"type":"highlight","icon":"🇮🇹","title":"Italy"},
                  {"type":"wheel","segments":[{"label":"Italy"},{"label":"Japan"}],"landing":1},
                  {"type":"actions","actions":[{"id":"spin","label":"Spin","primary":true}]},
                  {"type":"confetti","amount":3}
                ]}]}
                """
            )
        val blocks =
            MimosHttp(engine, API)
                .api(::PluginsApi)
                .getWeekPanels(LocalDate(2026, 11, 16))
                .body()
                .panels
                .single()
                .blocks
        assertEquals("Spin for a country.", (blocks[0] as PanelBlock.Text).block.text)
        assertEquals("Italy", (blocks[1] as PanelBlock.Highlight).block.title)
        assertEquals(1, (blocks[2] as PanelBlock.Wheel).block.landing)
        assertEquals("spin", (blocks[3] as PanelBlock.Actions).block.actions.single().id)
        assertEquals(JsonPrimitive("confetti"), (blocks[4] as PanelBlock.Unknown).json["type"])
    }

    @Test
    fun `a call accepts problem JSON, which is all a delete can answer with a body`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond("", HttpStatusCode.NoContent)
        }
        MimosHttp(engine, API).api(::RecipesApi).deleteRecipe("r1")
        val accept =
            requests.single().headers.getAll(HttpHeaders.Accept).orEmpty().joinToString(",")
        assertTrue("application/problem+json" in accept, accept)
    }
}
