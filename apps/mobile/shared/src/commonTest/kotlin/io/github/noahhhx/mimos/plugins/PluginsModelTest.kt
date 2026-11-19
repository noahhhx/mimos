package io.github.noahhhx.mimos.plugins

import io.github.noahhhx.mimos.api.apis.PluginSettingsApi
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val API = "https://api.mimos.example.com"

private const val COUNTRY_WEEK =
    """{"id":"country-week","name":"Country of the Week","enabled":false,
        "homepageUrl":"https://example.com/country-week"}"""

/** The plugin settings API: its list, and each choice it is sent, gated when asked. */
private class SettingsServer {
    var list: String? = "[$COUNTRY_WEEK]"
    var failSave = false
    var gate: CompletableDeferred<Unit>? = null
    val saves = Channel<Pair<String, Boolean>>(Channel.UNLIMITED)

    val engine = MockEngine { request ->
        if (request.method == HttpMethod.Put) {
            val body = MimosJson.parseToJsonElement((request.body as TextContent).text).jsonObject
            saves.trySend(
                request.url.encodedPath to body["enabled"]!!.jsonPrimitive.content.toBoolean()
            )
            gate?.await()
            if (failSave) respondError(HttpStatusCode.BadGateway)
            else respond("", HttpStatusCode.NoContent)
        } else {
            val answer = list ?: return@MockEngine respondError(HttpStatusCode.InternalServerError)
            respond(answer, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }
}

private fun TestScope.model(server: SettingsServer, changed: MutableList<Unit> = mutableListOf()) =
    PluginsModel(
        this,
        api = { MimosHttp(server.engine, API).api(::PluginSettingsApi) },
        onChanged = { changed += Unit },
    )

private suspend fun PluginsModel.loaded() =
    (state.first { it is PluginsState.Loaded } as PluginsState.Loaded).plugins

class PluginsModelTest {
    @Test
    fun `the instance's plugins load, each with the household's choice`() = runTest {
        val model = model(SettingsServer())
        model.load()
        val plugin = model.loaded().single()
        assertEquals("Country of the Week", plugin.name)
        assertEquals(false, plugin.enabled)
    }

    @Test
    fun `a list that fails to load says so`() = runTest {
        val model = model(SettingsServer().apply { list = null })
        model.load()
        assertIs<PluginsState.Failed>(model.state.first { it is PluginsState.Failed })
    }

    @Test
    fun `turning a plugin on shows at once, saves, and tells the plan`() = runTest {
        val server = SettingsServer().apply { gate = CompletableDeferred() }
        val changed = mutableListOf<Unit>()
        val model = model(server, changed)
        model.load()
        val plugin = model.loaded().single()

        model.setEnabled(plugin, true)

        assertEquals(true, (model.state.value as PluginsState.Loaded).plugins.single().enabled)
        assertEquals("country-week", model.saving.value)
        assertEquals("/api/v1/me/plugins/country-week" to true, server.saves.receive())
        server.gate!!.complete(Unit)
        model.saving.first { it == null }
        assertEquals(1, changed.size)
        assertNull(model.error.value)
    }

    @Test
    fun `a choice that fails to save goes back and says so`() = runTest {
        val server = SettingsServer().apply { failSave = true }
        val changed = mutableListOf<Unit>()
        val model = model(server, changed)
        model.load()
        val plugin = model.loaded().single()

        model.setEnabled(plugin, true)
        model.error.first { it != null }

        assertEquals("Could not turn Country of the Week on.", model.error.value)
        assertEquals(false, (model.state.value as PluginsState.Loaded).plugins.single().enabled)
        assertEquals(emptyList(), changed)
    }

    @Test
    fun `only an http or https homepage becomes a link`() {
        assertEquals("https://example.com/a", pluginHomepage("https://example.com/a"))
        assertEquals("http://example.com", pluginHomepage("http://example.com"))
        assertNull(pluginHomepage(null))
        assertNull(pluginHomepage(""))
        assertNull(pluginHomepage("javascript:alert(1)"))
        assertNull(pluginHomepage("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(pluginHomepage("file:///etc/passwd"))
        assertNull(pluginHomepage("not a url"))
    }
}
