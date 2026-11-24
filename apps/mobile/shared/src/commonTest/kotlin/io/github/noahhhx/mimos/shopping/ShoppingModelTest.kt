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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.io.IOException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val API = "https://api.mimos.example.com"
private val MONDAY = LocalDate(2026, 11, 16)

/** One week's shopping list, kept as the API would, with every write it is sent. */
private class ListServer {
    var list: ShoppingList? = null
    /** What every tick is answered with, instead of the item, while set. */
    var tickFailure: HttpStatusCode? = null
    /** No answer at all, as with no route to the server. */
    var unreachable = false
    val ticks = mutableListOf<Pair<String, Boolean>>()
    val added = mutableListOf<String>()
    val removed = mutableListOf<String>()
    val deletes = Channel<String>(Channel.UNLIMITED)
    /**
     * While set, a load reads the list, then waits on it to answer, as an answer slow to return.
     */
    var heldLoads: CompletableDeferred<Unit>? = null
    val loadsRead = Channel<Unit>(Channel.UNLIMITED)

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
        if (unreachable) throw IOException("Connection refused")
        fun json(value: Any) =
            respond(encode(value), headers = headersOf(HttpHeaders.ContentType, "application/json"))

        val path = request.url.segments.joinToString("/", prefix = "/")
        val body =
            (request.body as? TextContent)?.text?.let {
                MimosJson.parseToJsonElement(it).jsonObject
            }
        when {
            request.method == HttpMethod.Get -> {
                val segments = request.url.segments
                val week = LocalDate.parse(segments[segments.indexOf("plans") + 1])
                val seen = list?.copy(startDate = week)
                loadsRead.trySend(Unit)
                heldLoads?.await()
                seen?.let { json(it) } ?: respondError(HttpStatusCode.NotFound)
            }
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
                tickFailure?.let {
                    return@MockEngine respondError(it)
                }
                val item =
                    list!!.items.firstOrNull { it.id == id }?.copy(checked = checked)
                        ?: return@MockEngine respondError(HttpStatusCode.NotFound)
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

private fun TestScope.model(
    server: ListServer,
    store: ShoppingStore = MemoryShoppingStore(),
    network: FakeNetwork = FakeNetwork(),
) =
    ShoppingModel(
        this,
        api = { MimosHttp(server.engine, API).api(::ShoppingListsApi) },
        today = { LocalDate(2026, 11, 19) },
        store = store,
        network = network,
    )

private fun MemoryShoppingStore.storedLists() = decodeLists(load(ShoppingStore.Entry.Lists))

private fun MemoryShoppingStore.storedTicks() = decodeTicks(load(ShoppingStore.Entry.Ticks))

private fun ShoppingList.checked(id: String) = items.first { it.id == id }.checked

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
    fun `ticks made offline wait on the phone, then go once each, in order, when the network returns`() =
        runTest {
            val server = ListServer()
            val store = MemoryShoppingStore()
            val network = FakeNetwork()
            val model = model(server, store, network)
            model.generate()
            val list = model.loaded()
            network.online.value = false

            model.toggle(list.items.first { it.id == "onion" })
            model.toggle(list.items.first { it.id == "milk" })
            model.toggle(list.items.first { it.id == "onion" })
            advanceUntilIdle()
            assertEquals(
                listOf(TickStatus.Unsent, TickStatus.Unsent),
                model.ticks.value.values.map { it.status },
            )
            assertEquals(emptyList(), server.ticks)
            assertEquals(
                mapOf(
                    "onion" to Tick(MONDAY, false, TickStatus.Unsent),
                    "milk" to Tick(MONDAY, true, TickStatus.Unsent),
                ),
                store.storedTicks(),
            )

            network.online.value = true
            model.sendNow()
            model.ticks.first { it.isEmpty() }
            assertEquals(listOf("onion" to false, "milk" to true), server.ticks)
            assertNull(store.load(ShoppingStore.Entry.Ticks))
            assertTrue(store.storedLists().getValue(MONDAY).checked("milk"))
        }

    @Test
    fun `a tick for an item the server no longer has is dropped, and the list loads again`() =
        runTest {
            val server = ListServer()
            val model = model(server)
            model.generate()
            val onion = model.loaded().items.first { it.id == "onion" }
            server.list =
                server.list!!.let {
                    it.copy(items = it.items.map { i -> i.copy(id = i.id + "-2") })
                }

            model.toggle(onion)
            model.loaded { list -> list.items.any { it.id == "onion-2" } }
            assertEquals(emptyMap(), model.ticks.value)
            assertEquals(listOf("onion" to true), server.ticks)
        }

    @Test
    fun `a tick the server refuses, or that gets no answer, keeps waiting until it goes through`() =
        runTest {
            val server = ListServer()
            val model = model(server)
            model.generate()
            val milk = model.loaded().items.first { it.id == "milk" }

            server.tickFailure = HttpStatusCode.Unauthorized
            model.toggle(milk)
            model.ticks.first { it["milk"]?.status == TickStatus.Unsent }
            server.tickFailure = null
            server.unreachable = true
            model.sendNow()
            model.ticks.first { it["milk"]?.status == TickStatus.Unsent }
            assertEquals(true, model.ticks.value.getValue("milk").checked)
            assertFalse(model.loaded().checked("milk"))

            server.unreachable = false
            model.sendNow()
            model.ticks.first { it.isEmpty() }
            assertEquals(listOf("milk" to true, "milk" to true), server.ticks)
            assertTrue(model.loaded().checked("milk"))
        }

    @Test
    fun `a load answered after a tick lands does not undo the tick`() = runTest {
        val server = ListServer()
        val store = MemoryShoppingStore()
        val model = model(server, store)
        model.generate()
        val onion = model.loaded().items.first { it.id == "onion" }
        val bread = ShoppingListItem("bread", "bread", "Other", checked = false, added = true)
        server.list = server.list!!.let { it.copy(items = it.items + bread) }
        val held = CompletableDeferred<Unit>()
        server.heldLoads = held

        model.retry()
        server.loadsRead.receive()
        model.toggle(onion)
        model.ticks.first { it.isEmpty() }
        held.complete(Unit)

        assertTrue(model.loaded { list -> list.items.any { it.id == "bread" } }.checked("onion"))
        assertTrue(store.storedLists().getValue(MONDAY).checked("onion"))
    }

    @Test
    fun `an app started again with no answer shows the stored list and the ticks it kept`() =
        runTest {
            val server = ListServer()
            val store = MemoryShoppingStore()
            val network = FakeNetwork()
            val first = model(server, store, network)
            first.generate()
            first.loaded()
            network.online.value = false
            first.toggle(first.loaded().items.first { it.id == "onion" })

            server.unreachable = true
            val again = model(server, store, network)
            again.ensureLoaded()
            val shown = again.state.first { it is ListState.Loaded } as ListState.Loaded
            assertEquals(ListCopy.Stored, shown.copy)
            assertEquals(listOf("onion", "milk"), shown.list.items.map { it.id })
            assertEquals(Tick(MONDAY, true, TickStatus.Unsent), again.ticks.value["onion"])

            server.unreachable = false
            network.online.value = true
            again.sendNow()
            again.ticks.first { it.isEmpty() }
            again.state.first { (it as? ListState.Loaded)?.copy == ListCopy.Server }
            assertEquals(listOf("onion" to true), server.ticks)
            assertTrue(again.loaded().checked("onion"))
        }

    @Test
    fun `with no network, nothing tries to send on its own, even when the list loads`() = runTest {
        val server = ListServer()
        val store = MemoryShoppingStore()
        val network = FakeNetwork()
        model(server, store, network).apply {
            generate()
            network.online.value = false
            toggle(loaded().items.first { it.id == "onion" })
        }

        val again = model(server, store, network)
        again.ensureLoaded()
        assertEquals(
            ListCopy.Server,
            (again.state.first { it is ListState.Loaded } as ListState.Loaded).copy,
        )
        again.reconnected()
        advanceUntilIdle()
        assertEquals(TickStatus.Unsent, again.ticks.value.getValue("onion").status)
        assertEquals(emptyList(), server.ticks)
    }

    @Test
    fun `a week never loaded still fails when the server cannot be reached`() = runTest {
        val server = ListServer()
        val store = MemoryShoppingStore()
        model(server, store).apply {
            generate()
            loaded()
        }
        server.unreachable = true
        val model = model(server, store)
        model.nextWeek()
        model.state.first { it == ListState.Failed }
    }

    @Test
    fun `only the four weeks loaded last are kept on the phone`() = runTest {
        val server = ListServer()
        val store = MemoryShoppingStore()
        val model = model(server, store)
        model.generate()
        model.loaded()
        repeat(4) {
            model.nextWeek()
            model.loaded { it.startDate == model.monday.value }
        }
        val weeks = (1..4).map { LocalDate(2026, 11, 16).plus(DatePeriod(days = 7 * it)) }
        assertEquals(weeks, store.storedLists().keys.toList())
    }

    @Test
    fun `signing out forgets the lists and ticks kept on the phone`() = runTest {
        val server = ListServer()
        val store = MemoryShoppingStore()
        val network = FakeNetwork()
        val model = model(server, store, network)
        model.generate()
        network.online.value = false
        model.toggle(model.loaded().items.first())

        model.reset()
        assertEquals(emptyMap(), store.entries)
        server.unreachable = true
        val again = model(server, store, network)
        again.ensureLoaded()
        again.state.first { it == ListState.Failed }
        assertEquals(emptyMap(), again.ticks.value)
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
