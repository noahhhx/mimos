package io.github.noahhhx.mimos.shopping

import io.github.noahhhx.mimos.api.apis.ShoppingListsApi
import io.github.noahhhx.mimos.api.models.ShoppingList
import io.github.noahhhx.mimos.api.models.ShoppingListItem
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
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val API = "https://api.mimos.example.com"
private val MONDAY = LocalDate(2026, 11, 16)

/** One week's shopping list, kept as the API would, with every write it is sent. */
private class ListServer {
    var list: ShoppingList? = null
    var failTicks = false
    val ticks = mutableListOf<Pair<String, Boolean>>()
    val added = mutableListOf<String>()
    val removed = mutableListOf<String>()
    val deletes = Channel<String>(Channel.UNLIMITED)

    private val planned =
        listOf(
            ShoppingListItem(
                "onion",
                "onion",
                "Produce",
                checked = false,
                added = false,
                quantity = 2.0,
            ),
            ShoppingListItem("milk", "milk", "Dairy & Eggs", checked = false, added = false),
        )

    private fun encode(value: Any): String =
        when (value) {
            is ShoppingList -> MimosJson.encodeToString(ShoppingList.serializer(), value)
            is ShoppingListItem -> MimosJson.encodeToString(ShoppingListItem.serializer(), value)
            else -> error("unexpected $value")
        }

    private fun generated(): ShoppingList {
        val kept = list?.items?.filter { it.added }.orEmpty()
        return ShoppingList(MONDAY, Instant.parse("2026-11-16T09:00:00Z"), planned + kept).also {
            list = it
        }
    }

    val engine = MockEngine { request ->
        fun json(value: Any) =
            respond(encode(value), headers = headersOf(HttpHeaders.ContentType, "application/json"))

        val path = request.url.segments.joinToString("/", prefix = "/")
        val body =
            (request.body as? TextContent)?.text?.let {
                MimosJson.parseToJsonElement(it).jsonObject
            }
        when {
            request.method == HttpMethod.Get ->
                list?.let { json(it) } ?: respondError(HttpStatusCode.NotFound)
            request.method == HttpMethod.Post && path.endsWith("/items") -> {
                val name = body!!["name"]!!.jsonPrimitive.content
                added += name
                val current = list ?: generated()
                val item = ShoppingListItem(name, name, "Other", checked = false, added = true)
                list = current.copy(items = current.items + item)
                json(item)
            }
            request.method == HttpMethod.Post -> json(generated())
            request.method == HttpMethod.Patch -> {
                val id = path.substringAfterLast('/')
                val checked = body!!["checked"]!!.jsonPrimitive.content.toBoolean()
                ticks += id to checked
                if (failTicks) return@MockEngine respondError(HttpStatusCode.BadGateway)
                val item = list!!.items.first { it.id == id }.copy(checked = checked)
                list = list!!.copy(items = list!!.items.map { if (it.id == id) item else it })
                json(item)
            }
            request.method == HttpMethod.Delete -> {
                val id = path.substringAfterLast('/')
                removed += id
                deletes.trySend(id)
                list = list!!.copy(items = list!!.items.filter { it.id != id })
                respond("", HttpStatusCode.NoContent)
            }
            else -> respondError(HttpStatusCode.MethodNotAllowed)
        }
    }
}

private fun TestScope.model(server: ListServer) =
    ShoppingModel(
        this,
        api = { MimosHttp(server.engine, API).api(::ShoppingListsApi) },
        today = { LocalDate(2026, 11, 19) },
    )

private suspend fun ShoppingModel.loaded(where: (ShoppingList) -> Boolean = { true }) =
    (state.first { it is ListState.Loaded && where(it.list) } as ListState.Loaded).list

class ShoppingModelTest {
    @Test
    fun `a week with no list offers to generate one from the plan`() = runTest {
        val server = ListServer()
        val model = model(server)
        model.ensureLoaded()
        model.state.first { it == ListState.NotGenerated }

        model.generate()
        assertEquals(listOf("onion", "milk"), model.loaded().items.map { it.id })
        assertEquals(MONDAY, model.monday.value)
    }

    @Test
    fun `a tick shows at once, reaches the server, and then stands on the list itself`() = runTest {
        val server = ListServer()
        val model = model(server)
        model.generate()
        val onion = model.loaded().items.first { it.id == "onion" }

        model.toggle(onion)
        assertEquals(true, model.ticks.value["onion"]?.checked)
        model.ticks.first { it.isEmpty() }
        assertEquals(listOf("onion" to true), server.ticks)
        assertTrue(model.loaded().items.first { it.id == "onion" }.checked)
    }

    @Test
    fun `a tick that fails stays ticked as unsent until a retry gets it through`() = runTest {
        val server = ListServer().apply { failTicks = true }
        val model = model(server)
        model.generate()
        val milk = model.loaded().items.first { it.id == "milk" }

        model.toggle(milk)
        val unsent = model.ticks.first { it["milk"]?.unsent == true }.getValue("milk")
        assertEquals(true, unsent.checked)
        assertEquals(false, model.loaded().items.first { it.id == "milk" }.checked)

        server.failTicks = false
        model.retryTick("milk")
        model.ticks.first { it.isEmpty() }
        assertEquals(listOf("milk" to true, "milk" to true), server.ticks)
        assertTrue(model.loaded().items.first { it.id == "milk" }.checked)
    }

    @Test
    fun `the form refuses what the web refuses and sends nothing`() = runTest {
        val server = ListServer()
        val model = model(server)
        model.edit { it.copy(name = "milk", unit = "l") }
        model.add()
        assertEquals("Give an amount for the unit, or choose pieces.", model.error.value)
        assertEquals(emptyList(), server.added)
    }

    @Test
    fun `an added item joins the list, survives regenerating, and can be removed`() = runTest {
        val server = ListServer()
        val model = model(server)
        model.ensureLoaded()
        model.state.first { it == ListState.NotGenerated }

        model.edit { it.copy(name = " bin bags ") }
        model.add()
        val withBags = model.loaded { list -> list.items.any { it.id == "bin bags" } }
        assertEquals(AddItemFields(), model.fields.value)
        assertTrue(withBags.items.first { it.id == "bin bags" }.added)

        model.generate()
        model.generating.first { !it }
        val bags = model.loaded().items.first { it.id == "bin bags" }

        model.remove(bags)
        assertTrue(model.loaded().items.none { it.id == "bin bags" })
        assertEquals("bin bags", server.deletes.receive())
    }

    @Test
    fun `a generated item cannot be removed`() = runTest {
        val server = ListServer()
        val model = model(server)
        model.generate()
        model.remove(model.loaded().items.first())
        assertEquals(2, model.loaded().items.size)
        assertEquals(emptyList(), server.removed)
    }
}
