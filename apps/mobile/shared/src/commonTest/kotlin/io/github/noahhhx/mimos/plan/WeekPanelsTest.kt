package io.github.noahhhx.mimos.plan

import io.github.noahhhx.mimos.api.apis.PluginsApi
import io.github.noahhhx.mimos.api.models.PanelButton
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val PANELS_API = "https://api.mimos.example.com"
private val THIS_MONDAY = LocalDate(2026, 11, 16)
private val NEXT_MONDAY = LocalDate(2026, 11, 23)

private const val SPIN = """{"id":"spin","label":"Spin","primary":true}"""

private fun wheelPanel(summary: String? = null, landing: Int? = null): String {
    val landed = landing?.let { ""","landing":$it""" } ?: ""
    val summaryJson = summary?.let { ""","summary":{"icon":"🇮🇹","label":"$it"}""" } ?: ""
    return """{"pluginId":"country-week","pluginName":"Country of the Week"$summaryJson,
        "blocks":[{"type":"text","text":"197 countries left."},
        {"type":"wheel","segments":[{"label":"Peru"},{"label":"Italy"}]$landed},
        {"type":"actions","actions":[$SPIN]}]}"""
}

/** A plugin server: each week's panels, gated when asked, and every button press it is sent. */
private class PanelServer {
    var pressAnswer: () -> Pair<HttpStatusCode, String> = {
        HttpStatusCode.OK to wheelPanel(landing = 1)
    }
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    val failWeeks = mutableSetOf<String>()
    val presses = Channel<Pair<String, JsonObject>>(Channel.UNLIMITED)

    val engine = MockEngine { request ->
        val path = request.url.encodedPath
        val week = path.removePrefix("/api/v1/plans/").substringBefore('/')
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        if (request.method == HttpMethod.Post) {
            val body = MimosJson.parseToJsonElement((request.body as TextContent).text).jsonObject
            presses.trySend(path to body)
            val (status, answer) = pressAnswer()
            return@MockEngine if (status.value >= 400) {
                respond(
                    answer,
                    status,
                    headersOf(HttpHeaders.ContentType, "application/problem+json"),
                )
            } else respond(answer, headers = json)
        }
        gates[week]?.await()
        if (week in failWeeks) respondError(HttpStatusCode.InternalServerError)
        else respond("""{"panels":[${wheelPanel(summary = "Week $week")}]}""", headers = json)
    }
}

private fun TestScope.panelsModel(server: PanelServer, pressed: MutableList<LocalDate>) =
    WeekPanelsModel(
        this,
        { MimosHttp(server.engine, PANELS_API).api(::PluginsApi) },
        { pressed += it },
    )

private suspend fun WeekPanelsModel.loaded(monday: LocalDate): WeekPanels =
    state.first { it?.monday == monday }!!

private val spin = PanelButton("spin", "Spin", primary = true)

class WeekPanelsTest {
    @Test
    fun `a week's panels load collapsed, and a newer week's replace a slower older answer`() =
        runTest {
            val server = PanelServer()
            val slow = CompletableDeferred<Unit>().also { server.gates["$THIS_MONDAY"] = it }
            val model = panelsModel(server, mutableListOf())

            model.ensureLoaded(THIS_MONDAY)
            model.ensureLoaded(NEXT_MONDAY)
            val next = model.loaded(NEXT_MONDAY)
            slow.complete(Unit)

            assertEquals("Week $NEXT_MONDAY", next.panels.single().panel.summary?.label)
            assertEquals(false, next.panels.single().open)
            assertEquals(NEXT_MONDAY, model.state.value?.monday, "the older answer never lands")
        }

    @Test
    fun `a press sends the button, replaces the panel, and spins a wheel with a landing`() =
        runTest {
            val server = PanelServer()
            val pressed = mutableListOf<LocalDate>()
            val model = panelsModel(server, pressed)
            model.load(THIS_MONDAY)
            model.loaded(THIS_MONDAY)
            model.toggle("country-week")

            model.press("country-week", PanelButton("choose", "Choose Italy", "IT"))

            val (path, body) = server.presses.receive()
            assertEquals("/api/v1/plans/$THIS_MONDAY/panels/country-week/actions", path)
            assertEquals("choose", body["id"]!!.jsonPrimitive.content)
            assertEquals("IT", body["value"]!!.jsonPrimitive.content)
            val panel = model.state.first { it!!.panels.single().spinning }!!.panels.single()
            assertTrue(panel.open)
            assertEquals(false, panel.buttonsEnabled, "buttons wait for the wheel")
            assertEquals(listOf(THIS_MONDAY), pressed, "the week's suggestions are asked again")

            model.spinStopped("country-week")
            assertTrue(model.state.value!!.panels.single().buttonsEnabled)
        }

    @Test
    fun `an answer with no landing does not spin`() = runTest {
        val server = PanelServer().apply { pressAnswer = { HttpStatusCode.OK to wheelPanel() } }
        val model = panelsModel(server, mutableListOf())
        model.load(THIS_MONDAY)
        model.loaded(THIS_MONDAY)

        model.press("country-week", spin)
        server.presses.receive()
        val panel = model.state.first { !it!!.panels.single().pressing }!!.panels.single()

        assertEquals(false, panel.spinning)
    }

    @Test
    fun `closing a panel mid-spin ends the spin`() = runTest {
        val server = PanelServer()
        val model = panelsModel(server, mutableListOf())
        model.load(THIS_MONDAY)
        model.loaded(THIS_MONDAY)
        model.toggle("country-week")
        model.press("country-week", spin)
        model.state.first { it!!.panels.single().spinning }

        model.toggle("country-week")

        val panel = model.state.value!!.panels.single()
        assertEquals(false, panel.open)
        assertEquals(false, panel.spinning)
    }

    @Test
    fun `a failed press says why, in the problem's words or the plugin's name`() = runTest {
        val server =
            PanelServer().apply {
                pressAnswer = {
                    HttpStatusCode.BadGateway to
                        """{"title":"Bad Gateway","status":502,"detail":"The plugin did not answer."}"""
                }
            }
        val pressed = mutableListOf<LocalDate>()
        val model = panelsModel(server, pressed)
        model.load(THIS_MONDAY)
        model.loaded(THIS_MONDAY)

        model.press("country-week", spin)
        val failed = model.state.first { it!!.panels.single().error != null }!!.panels.single()
        assertEquals("The plugin did not answer.", failed.error)
        assertEquals(false, failed.pressing)
        assertEquals(emptyList(), pressed)

        server.pressAnswer = { HttpStatusCode.BadGateway to "not json" }
        model.press("country-week", spin)
        val fallback =
            model.state.first { it!!.panels.single().error?.startsWith("Country") == true }
        assertEquals(
            "Country of the Week could not do that. Try again.",
            fallback!!.panels.single().error,
        )
    }

    @Test
    fun `a week whose panels fail has none`() = runTest {
        val failing = LocalDate(2026, 11, 30)
        val server = PanelServer().apply { failWeeks += "$failing" }
        val model = panelsModel(server, mutableListOf())

        model.load(failing)
        assertEquals(emptyList(), model.loaded(failing).panels)
        model.reset()
        assertNull(model.state.value)
    }
}
