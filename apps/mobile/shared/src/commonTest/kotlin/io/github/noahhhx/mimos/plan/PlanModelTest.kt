package io.github.noahhhx.mimos.plan

import io.github.noahhhx.mimos.api.apis.HouseholdApi
import io.github.noahhhx.mimos.api.apis.PlanningApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.RecipeSummary
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
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val API = "https://api.mimos.example.com"
private val THURSDAY = LocalDate(2026, 11, 19)
private const val MONDAY = "2026-11-16"

private const val ME = """{"id":"me","displayName":"Sam Lee","you":true}"""
private const val PARTNER = """{"id":"partner","displayName":"Alex","you":false}"""

private fun recipeEntry(id: String, mealType: String, servings: Double, vararg diners: String) =
    """{"id":"$id","date":"$MONDAY","mealType":"$mealType","recipeId":"r-$id",
        "recipeTitle":"Stew $id","servings":$servings,"diners":[${diners.joinToString(",")}]}"""

/** A plan server that answers from what it holds and records every write it is sent. */
private class Server {
    var members = listOf(ME)
    var entries = mutableListOf<String>()
    var failHousehold = false
    var failTotals = false
    var failWrites = false

    /** Each write as "METHOD path" with its body, as it arrives. */
    val writes = Channel<Pair<String, JsonObject?>>(Channel.UNLIMITED)
    val plansRead = Channel<String>(Channel.UNLIMITED)

    val engine = MockEngine { request ->
        val path = request.url.encodedPath
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val body =
            (request.body as? TextContent)?.text?.let {
                MimosJson.parseToJsonElement(it).jsonObject
            }
        if (request.method != HttpMethod.Get) {
            writes.trySend("${request.method.value} $path" to body)
            if (failWrites) return@MockEngine respondError(HttpStatusCode.BadRequest)
        }
        when {
            path == "/api/v1/household" ->
                if (failHousehold) respondError(HttpStatusCode.InternalServerError)
                else respond("""{"members":[${members.joinToString(",")}]}""", headers = json)
            path.endsWith("/my-totals") ->
                if (failTotals) respondError(HttpStatusCode.InternalServerError)
                else {
                    val start = path.removePrefix("/api/v1/plans/").removeSuffix("/my-totals")
                    val days =
                        (16..22).joinToString(",") {
                            """{"date":"2026-11-$it","total":{"calories":500},"meals":1,
                                "mealsWithoutNutrition":0}"""
                        }
                    respond("""{"startDate":"$start","days":[$days]}""", headers = json)
                }
            request.method == HttpMethod.Get && path.startsWith("/api/v1/plans/") -> {
                val start = path.removePrefix("/api/v1/plans/")
                plansRead.trySend(start)
                respond(
                    """{"startDate":"$start","entries":[${entries.joinToString(",")}]}""",
                    headers = json,
                )
            }
            request.method == HttpMethod.Delete -> {
                entries.removeAll { it.contains("\"id\":\"${path.substringAfterLast('/')}\"") }
                respond("", HttpStatusCode.NoContent)
            }
            request.method == HttpMethod.Post ->
                respond(recipeEntry("new", "DINNER", 2.0, ME), headers = json)
            request.method == HttpMethod.Patch ->
                respond(recipeEntry("e1", "DINNER", 2.0, ME), headers = json)
            path == "/api/v1/ingredients" ->
                respond(
                    """[{"slug":"banana","name":"Banana","isShared":true,"basis":"PER_PIECE",
                        "nutrition":{"calories":105}}]""",
                    headers = json,
                )
            else -> respond("[]", headers = json)
        }
    }

    suspend fun nextWrite(): Pair<String, JsonObject?> = writes.receive()
}

private class MemoryViewStore(var saved: PlanView? = null) : PlanViewStore {
    override fun load() = saved

    override fun save(view: PlanView) {
        saved = view
    }
}

private fun TestScope.model(server: Server, store: PlanViewStore = MemoryViewStore()) =
    PlanModel(
        this,
        store,
        planning = { MimosHttp(server.engine, API).api(::PlanningApi) },
        household = { MimosHttp(server.engine, API).api(::HouseholdApi) },
        recipes = { MimosHttp(server.engine, API).api(::RecipesApi) },
        today = { THURSDAY },
        searchDelayMillis = 0,
    )

private suspend fun PlanModel.loaded(): PlanWeek =
    (week.first { it is WeekState.Loaded } as WeekState.Loaded).week

private suspend fun PlanModel.settled() = busy.first { !it }

class PlanModelTest {
    @Test
    fun `the week opens on today's Monday with its members and totals`() = runTest {
        val server = Server().apply { entries += recipeEntry("e1", "DINNER", 2.0, ME) }
        val model = model(server)

        model.ensureLoaded()
        val week = model.loaded()

        assertEquals(MONDAY, server.plansRead.receive())
        assertEquals(LocalDate(2026, 11, 16), week.monday)
        assertEquals(listOf("e1"), week.entries.map { it.id })
        assertEquals(false, week.shared)
        assertEquals(500.0, week.total(LocalDate(2026, 11, 18))?.total?.calories)
    }

    @Test
    fun `without the household or the totals the week still plans`() = runTest {
        val server =
            Server().apply {
                failHousehold = true
                failTotals = true
            }
        val model = model(server)

        model.ensureLoaded()
        val week = model.loaded()

        assertEquals(emptyList(), week.members)
        assertNull(week.totals)
        assertEquals(false, week.totalsShown(PlanView.Mine), "no totals to show")
    }

    @Test
    fun `the weeks move by seven days and back to this one`() = runTest {
        val server = Server()
        val model = model(server)
        model.ensureLoaded()
        assertEquals(MONDAY, server.plansRead.receive())

        model.nextWeek()
        assertEquals("2026-11-23", server.plansRead.receive())
        model.previousWeek()
        model.previousWeek()
        assertEquals("2026-11-09", model.monday.value.toString())
        model.thisWeek()
        assertEquals(LocalDate(2026, 11, 16), model.loaded().monday)
    }

    @Test
    fun `a recipe planned at dinner in a shared household cooks a serving for each member`() =
        runTest {
            val server = Server().apply { members = listOf(ME, PARTNER) }
            val model = model(server)
            model.ensureLoaded()
            model.loaded()

            model.openPicker(LocalDate(2026, 11, 17), MealType.DINNER)
            val recipe = model.picker.first { it.recipes != null }
            assertEquals(emptyList(), recipe.recipes)
            model.plan(
                MimosJson.decodeFromString<RecipeSummary>(
                    """{"id":"soup","title":"Soup","description":"","servings":4,
                        "isLibrary":true,"tags":[],"nutrition":{},
                        "createdAt":"2026-11-01T10:00:00Z","updatedAt":"2026-11-01T10:00:00Z"}"""
                )
            )

            val (call, body) = server.nextWrite()
            assertEquals("POST /api/v1/plans/$MONDAY/entries", call)
            assertEquals("2026-11-17", body!!["date"]!!.jsonPrimitive.content)
            assertEquals(2.0, body["servings"]!!.jsonPrimitive.content.toDouble())
            assertEquals("soup", body["recipeId"]!!.jsonPrimitive.content)
            assertTrue("diners" !in body, "the API gives dinner every member")
            model.settled()
            assertEquals(PlanRoute.Week, model.route.value)
        }

    @Test
    fun `a household of one plans 2 servings and a food at one portion`() = runTest {
        val server = Server()
        val model = model(server)
        model.ensureLoaded()
        model.loaded()

        model.openPicker(LocalDate(2026, 11, 16), MealType.LUNCH)
        model.search("ban")
        val found = model.picker.first { it.query == "ban" && it.foods.isNotEmpty() }
        model.plan(found.foods.single())

        val (_, body) = server.nextWrite()
        assertEquals(1.0, body!!["servings"]!!.jsonPrimitive.content.toDouble())
        assertEquals("banana", body["food"]!!.jsonObject["catalogSlug"]!!.jsonPrimitive.content)
        assertTrue("unit" !in body["food"]!!.jsonObject, "a piece has no unit")
    }

    @Test
    fun `servings step by half and stop at the API's bounds`() = runTest {
        val server =
            Server().apply {
                entries += recipeEntry("e1", "DINNER", 2.0, ME)
                entries += recipeEntry("e2", "LUNCH", 0.5, ME)
            }
        val model = model(server)
        model.ensureLoaded()
        val week = model.loaded()

        model.fewerServings(week.entries[1])
        model.fewerServings(week.entries[0])

        val (call, body) = server.nextWrite()
        assertEquals("PATCH /api/v1/plans/$MONDAY/entries/e1", call, "0.5 servings go no lower")
        assertEquals(1.5, body!!["servings"]!!.jsonPrimitive.content.toDouble())
    }

    @Test
    fun `a meal's last diner cannot be taken off it`() = runTest {
        val server =
            Server().apply {
                members = listOf(ME, PARTNER)
                entries += recipeEntry("e1", "DINNER", 2.0, ME)
            }
        val model = model(server)
        model.ensureLoaded()
        val entry = model.loaded().entries.single()

        model.toggleDiner(entry, "me")
        model.toggleDiner(entry, "partner")

        val (call, body) = server.nextWrite()
        assertEquals("PATCH /api/v1/plans/$MONDAY/entries/e1", call)
        assertEquals(
            listOf("me", "partner"),
            body!!["diners"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `removing a meal closes its inspector and reloads the week`() = runTest {
        val server = Server().apply { entries += recipeEntry("e1", "DINNER", 2.0, ME) }
        val model = model(server)
        model.ensureLoaded()
        val entry = model.loaded().entries.single()
        model.openMeal(entry)

        model.remove(entry)

        assertEquals("DELETE /api/v1/plans/$MONDAY/entries/e1", server.nextWrite().first)
        model.settled()
        assertEquals(PlanRoute.Week, model.route.value)
        assertEquals(emptyList(), model.loaded().entries)
    }

    @Test
    fun `a change the API refuses says so and keeps the meal open`() = runTest {
        val server =
            Server().apply {
                entries += recipeEntry("e1", "DINNER", 2.0, ME)
                failWrites = true
            }
        val model = model(server)
        model.ensureLoaded()
        val entry = model.loaded().entries.single()
        model.openMeal(entry)

        model.moreServings(entry)
        server.nextWrite()
        model.settled()

        assertEquals("Could not change the servings.", model.error.value)
        assertEquals(PlanRoute.Meal("e1"), model.route.value)
    }

    @Test
    fun `choosing Mine is remembered and closes a meal the caller does not eat`() = runTest {
        val store = MemoryViewStore(PlanView.Everyone)
        val server =
            Server().apply {
                members = listOf(ME, PARTNER)
                entries += recipeEntry("theirs", "LUNCH", 1.0, PARTNER)
            }
        val model = model(server, store)
        model.ensureLoaded()
        val week = model.loaded()
        assertEquals(PlanView.Everyone, model.view.value)
        assertEquals(listOf("theirs"), week.shown(PlanView.Everyone).map { it.id })
        model.openMeal(week.entries.single())

        model.chooseView(PlanView.Mine)

        assertEquals(PlanView.Mine, store.saved)
        assertEquals(PlanRoute.Week, model.route.value)
        assertEquals(emptyList(), week.shown(PlanView.Mine))
    }

    @Test
    fun `a week that fails to load says so`() = runTest {
        val server = Server()
        val failing =
            PlanModel(
                this,
                MemoryViewStore(),
                planning = {
                    MimosHttp(MockEngine { respondError(HttpStatusCode.BadGateway) }, API)
                        .api(::PlanningApi)
                },
                household = { MimosHttp(server.engine, API).api(::HouseholdApi) },
                recipes = { MimosHttp(server.engine, API).api(::RecipesApi) },
                today = { THURSDAY },
            )

        failing.ensureLoaded()

        assertIs<WeekState.Failed>(failing.week.first { it is WeekState.Failed })
    }
}
