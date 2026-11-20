package io.github.noahhhx.mimos.fuel

import io.github.noahhhx.mimos.api.apis.FuelApi
import io.github.noahhhx.mimos.api.apis.IntervalsIcuApi
import io.github.noahhhx.mimos.api.models.FuelGoal
import io.github.noahhhx.mimos.api.models.FuelSex
import io.github.noahhhx.mimos.api.models.IntervalsIcuConnectionStatus
import io.github.noahhhx.mimos.client.MimosHttp
import io.github.noahhhx.mimos.client.MimosJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val API = "https://api.mimos.example.com"

private val PROBLEM = headersOf(HttpHeaders.ContentType, "application/problem+json")

/** A person's fuel profile and intervals.icu connection, kept as the API would. */
private class FuelServer {
    var profile = """{"enabled":false,"sex":"UNSPECIFIED","goal":"FUEL","proteinGPerKg":1.6}"""
    /** The connection as `GET /api/v1/me/intervals-icu` answers it; null is the feature off. */
    var connection: String? = """{"status":"DISCONNECTED"}"""
    var refuseProfile: String? = null
    var refuseKey = false
    var failSync = false

    /** Each write as "METHOD path" with its body, as it arrives. */
    val writes = Channel<Pair<String, JsonObject?>>(Channel.UNLIMITED)

    val engine = MockEngine { request ->
        val path = request.url.encodedPath
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val body =
            (request.body as? TextContent)?.text?.let {
                MimosJson.parseToJsonElement(it).jsonObject
            }
        if (request.method != HttpMethod.Get)
            writes.trySend("${request.method.value} $path" to body)
        when {
            path == "/api/v1/me/fuel-profile" && request.method == HttpMethod.Get ->
                respond(profile, headers = json)
            path == "/api/v1/me/fuel-profile" ->
                refuseProfile?.let {
                    respond(
                        """{"title":"Bad Request","status":400,"detail":"$it"}""",
                        HttpStatusCode.BadRequest,
                        PROBLEM,
                    )
                }
                    ?: run {
                        profile =
                            """{"enabled":${body!!["enabled"]},"sex":${body["sex"]},
                                "goal":${body["goal"]},"proteinGPerKg":1.6,
                                "weightKg":${body["weightKg"]}}"""
                        respond(profile, headers = json)
                    }
            path == "/api/v1/me/intervals-icu" && request.method == HttpMethod.Get ->
                connection?.let { respond(it, headers = json) }
                    ?: respond(
                        """{"title":"Not Found","status":404}""",
                        HttpStatusCode.NotFound,
                        PROBLEM,
                    )
            path == "/api/v1/me/intervals-icu" && request.method == HttpMethod.Put ->
                if (refuseKey) {
                    respond(
                        """{"title":"Bad Request","status":400,
                            "detail":"intervals.icu did not accept this athlete id and API key."}""",
                        HttpStatusCode.BadRequest,
                        PROBLEM,
                    )
                } else {
                    respond(
                        """{"status":"CONNECTED","athleteId":"i1","hints":{}}""",
                        headers = json,
                    )
                }
            path == "/api/v1/me/intervals-icu/sync" ->
                if (failSync) respondError(HttpStatusCode.BadGateway)
                else
                    respond(
                        """{"status":"CONNECTED","athleteId":"i1",
                            "lastSyncAt":"2026-11-19T07:30:00Z","hints":{"weightKg":70.8}}""",
                        headers = json,
                    )
            path == "/api/v1/me/intervals-icu" && request.method == HttpMethod.Delete ->
                respond("", HttpStatusCode.NoContent)
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    suspend fun nextWrite(): Pair<String, JsonObject?> = writes.receive()
}

private class Changes {
    var count = 0
}

private fun TestScope.model(server: FuelServer, changes: Changes = Changes()) =
    FuelModel(
        this,
        fuel = { MimosHttp(server.engine, API).api(::FuelApi) },
        intervalsIcu = { MimosHttp(server.engine, API).api(::IntervalsIcuApi) },
        onChanged = { changes.count++ },
    )

private suspend fun FuelModel.form(): ProfileForm =
    (profile.first { it is ProfileState.Loaded } as ProfileState.Loaded).form

private suspend fun FuelModel.loadedConnection() =
    (connection.first { it is ConnectionState.Loaded } as ConnectionState.Loaded).connection

class FuelModelTest {
    @Test
    fun `a profile never saved opens with targets off and no weight`() = runTest {
        val model = model(FuelServer())

        model.load()

        assertEquals(FuelDraft(proteinGPerKg = "1.6"), model.form().draft)
        assertEquals(IntervalsIcuConnectionStatus.DISCONNECTED, model.loadedConnection().status)
    }

    @Test
    fun `an instance without intervals icu hides its section`() = runTest {
        val model = model(FuelServer().apply { connection = null })

        model.load()

        assertEquals(ConnectionState.Off, model.connection.first { it != ConnectionState.Loading })
    }

    @Test
    fun `nothing is sent without a weight, and a saved profile turns targets on`() = runTest {
        val server = FuelServer()
        val changes = Changes()
        val model = model(server, changes)
        model.load()
        model.form()

        model.edit { it.copy(enabled = true) }
        model.save()
        assertEquals(listOf(FuelField.WeightKg), model.form().fieldErrors)
        assertTrue(server.writes.tryReceive().isFailure)

        model.edit { it.copy(weightKg = "70", sex = FuelSex.MALE) }
        model.save()
        val (write, body) = server.nextWrite()
        assertEquals("PUT /api/v1/me/fuel-profile", write)
        assertEquals("70.0", body!!["weightKg"]!!.jsonPrimitive.content)
        assertEquals("true", body["enabled"]!!.jsonPrimitive.content)
        val saved = model.profile.first { (it as? ProfileState.Loaded)?.form?.saved == true }
        assertIs<ProfileState.Loaded>(saved)
        assertEquals("70", saved.form.draft.weightKg)
        assertEquals(1, changes.count, "the plan's targets follow")
    }

    @Test
    fun `fields the API names are marked, and anything else is a plain failure`() = runTest {
        val server = FuelServer().apply { refuseProfile = "weightKg must be between 30 and 250" }
        val model = model(server)
        model.load()
        model.form()

        model.edit { it.copy(weightKg = "20") }
        model.save()
        server.nextWrite()
        val marked =
            model.profile.first {
                (it as? ProfileState.Loaded)?.form?.fieldErrors?.isNotEmpty() == true
            }
        assertEquals(listOf(FuelField.WeightKg), (marked as ProfileState.Loaded).form.fieldErrors)
        assertNull(marked.form.error)

        server.refuseProfile = "something else"
        model.save()
        server.nextWrite()
        val failed = model.profile.first { (it as? ProfileState.Loaded)?.form?.error != null }
        assertEquals(
            "Could not save your fuel settings.",
            (failed as ProfileState.Loaded).form.error,
        )
    }

    @Test
    fun `a goal sets its protein`() = runTest {
        val model = model(FuelServer())
        model.load()
        model.form()

        model.chooseGoal(GOALS.first { it.value == FuelGoal.GAIN })

        assertEquals("1.8", model.form().draft.proteinGPerKg)
    }

    @Test
    fun `connecting sends the key once, forgets it, and syncs at once`() = runTest {
        val server = FuelServer()
        val changes = Changes()
        val model = model(server, changes)
        model.load()
        model.loadedConnection()

        model.editAthleteId(" i1 ")
        model.editApiKey("fake-intervals-icu-key")
        model.connect()

        val (connect, body) = server.nextWrite()
        assertEquals("PUT /api/v1/me/intervals-icu", connect)
        assertEquals("i1", body!!["athleteId"]!!.jsonPrimitive.content)
        assertEquals("fake-intervals-icu-key", body["apiKey"]!!.jsonPrimitive.content)
        assertEquals("POST /api/v1/me/intervals-icu/sync", server.nextWrite().first)
        model.link.first { !it.busy }
        assertEquals("", model.link.value.apiKey)
        assertEquals(70.8, model.loadedConnection().hints?.weightKg)
        assertEquals(2, changes.count)
    }

    @Test
    fun `a key intervals icu refuses says why and keeps the form`() = runTest {
        val server = FuelServer().apply { refuseKey = true }
        val model = model(server)
        model.load()
        model.loadedConnection()

        model.editAthleteId("i1")
        model.editApiKey("not-the-key")
        model.connect()
        server.nextWrite()

        val link = model.link.first { it.problem != null }
        assertEquals("intervals.icu did not accept this athlete id and API key.", link.problem)
        assertEquals(IntervalsIcuConnectionStatus.DISCONNECTED, model.loadedConnection().status)
        assertTrue(server.writes.tryReceive().isFailure, "no sync")
    }

    @Test
    fun `a failed refresh says so, and disconnecting asks first`() = runTest {
        val server =
            FuelServer().apply {
                connection = """{"status":"CONNECTED","athleteId":"i1","hints":{}}"""
                failSync = true
            }
        val model = model(server)
        model.load()
        model.loadedConnection()

        model.sync()
        server.nextWrite()
        assertEquals(IntervalsIcuFailures.SYNC, model.link.first { it.problem != null }.problem)

        model.startDisconnect()
        assertTrue(model.link.value.confirmingDisconnect)
        model.disconnect()
        assertEquals("DELETE /api/v1/me/intervals-icu", server.nextWrite().first)
        model.connection.first {
            (it as? ConnectionState.Loaded)?.connection?.status ==
                IntervalsIcuConnectionStatus.DISCONNECTED
        }
        assertEquals(LinkForm(), model.link.first { !it.busy })
    }

    @Test
    fun `signing out forgets a typed key`() = runTest {
        val model = model(FuelServer())
        model.load()
        model.loadedConnection()
        model.editApiKey("secret")

        model.reset()

        assertEquals("", model.link.value.apiKey)
        assertEquals(ProfileState.Loading, model.profile.value)
    }
}
