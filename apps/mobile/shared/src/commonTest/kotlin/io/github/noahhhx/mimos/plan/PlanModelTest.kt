package io.github.noahhhx.mimos.plan

import io.github.noahhhx.mimos.api.apis.FoodProductsApi
import io.github.noahhhx.mimos.api.apis.FuelApi
import io.github.noahhhx.mimos.api.apis.HouseholdApi
import io.github.noahhhx.mimos.api.apis.LoggingApi
import io.github.noahhhx.mimos.api.apis.PlanningApi
import io.github.noahhhx.mimos.api.apis.PluginsApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.client.MimosHttp
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.fuel.TrainingFailures
import io.github.noahhhx.mimos.fuel.fuelDaysJson
import io.github.noahhhx.mimos.fuel.sessionJson
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

private const val PROBLEM_MINUTES =
    """{"title":"Bad Request","status":400,"detail":"durationMinutes must be between 1 and 1440"}"""

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
    var failMinutes = false
    /** Each day's target calories while targets are on, and its sessions. */
    var targets = mapOf<LocalDate, Int>()
    var sessions = mapOf<LocalDate, List<String>>()
    val fuelRead = Channel<Pair<LocalDate, LocalDate>>(Channel.UNLIMITED)
    var failWrites = false
    var failIngredient = false
    var suggestions = "[]"
    /** What looking up any barcode finds. */
    var product = ""
    var failSuggestions = false
    /** The recipes whose plan entries the server refuses. */
    var failRecipes = setOf<String>()
    val suggestionsRead = Channel<String>(Channel.UNLIMITED)

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
            val recipeId = body?.get("recipeId")?.jsonPrimitive?.content
            if (recipeId in failRecipes) return@MockEngine respondError(HttpStatusCode.BadRequest)
        }
        when {
            path.endsWith("/suggestions") -> {
                suggestionsRead.trySend(path.removePrefix("/api/v1/plans/").substringBefore('/'))
                if (failSuggestions) respondError(HttpStatusCode.InternalServerError)
                else respond("""{"suggestions":$suggestions}""", headers = json)
            }
            path.endsWith("/panels") -> respond("""{"panels":[]}""", headers = json)
            path.startsWith("/api/v1/food-products/") -> respond(product, headers = json)
            path == "/api/v1/household" ->
                if (failHousehold) respondError(HttpStatusCode.InternalServerError)
                else respond("""{"members":[${members.joinToString(",")}]}""", headers = json)
            path == "/api/v1/me/fuel" ->
                if (failTotals) respondError(HttpStatusCode.InternalServerError)
                else {
                    val from = LocalDate.parse(request.url.parameters["from"]!!)
                    val to = LocalDate.parse(request.url.parameters["to"]!!)
                    fuelRead.trySend(from to to)
                    respond(
                        fuelDaysJson(from, to, planned = 500.0, targets, sessions),
                        headers = json,
                    )
                }
            request.method == HttpMethod.Post && path == "/api/v1/me/training" ->
                if (failMinutes) {
                    respond(
                        PROBLEM_MINUTES,
                        HttpStatusCode.BadRequest,
                        headersOf(HttpHeaders.ContentType, "application/problem+json"),
                    )
                } else respond(sessionJson("s-new", THURSDAY, 45), headers = json)
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
            request.method == HttpMethod.Post && path == "/api/v1/ingredients" ->
                if (failIngredient) {
                    respond(
                        """{"title":"Bad Request","status":400,"detail":"Name is taken."}""",
                        HttpStatusCode.BadRequest,
                        headersOf(HttpHeaders.ContentType, "application/problem+json"),
                    )
                } else {
                    respond(
                        """{"slug":"dragon-fruit-x1","name":"Dragon fruit","isShared":false,
                            "basis":"PER_100_G","nutrition":{"calories":60}}""",
                        headers = json,
                    )
                }
            request.method == HttpMethod.Post && path == "/api/v1/logs" ->
                respond(
                    """{"id":"log-1","date":"$MONDAY","mealType":"DINNER","description":"Logged",
                        "servings":1,"nutrition":{}}""",
                    headers = json,
                )
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
        plugins = { MimosHttp(server.engine, API).api(::PluginsApi) },
        logging = { MimosHttp(server.engine, API).api(::LoggingApi) },
        foodProducts = { MimosHttp(server.engine, API).api(::FoodProductsApi) },
        fuel = { MimosHttp(server.engine, API).api(::FuelApi) },
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
        assertNull(week.fuel)
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
    fun `a food the catalog lacks is added, then planned at a new food's amount`() = runTest {
        val server = Server()
        val model = model(server)
        model.ensureLoaded()
        model.loaded()
        model.openPicker(LocalDate(2026, 11, 16), MealType.LUNCH)

        model.search("ban")
        val banana = model.picker.first { it.query == "ban" && it.recipes != null }
        assertTrue(banana.offersNewIngredient, "nothing is called ban")
        model.search("Banana")
        assertTrue(
            !model.picker.first { it.query == "Banana" && it.recipes != null }.offersNewIngredient,
            "Banana is in the catalog",
        )

        model.search(" Dragon fruit ")
        model.picker.first { it.query == " Dragon fruit " && it.recipes != null }
        model.startNewIngredient()
        assertEquals("Dragon fruit", model.picker.value.adding?.name)
        assertTrue(!model.picker.value.offersNewIngredient, "the panel replaces the offer")

        model.editNewIngredient { it.copy(calories = "60") }
        model.saveNewIngredient()
        assertEquals(
            "Fill in all four values, using 0 when there is none.",
            model.picker.value.adding?.error,
        )

        model.editNewIngredient { it.copy(proteinG = "1", carbsG = "13", fatG = "0") }
        model.saveNewIngredient()
        val (created, ingredient) = server.nextWrite()
        assertEquals("POST /api/v1/ingredients", created)
        assertEquals("Dragon fruit", ingredient!!["name"]!!.jsonPrimitive.content)
        assertEquals("PER_100_G", ingredient["basis"]!!.jsonPrimitive.content)

        val (planned, body) = server.nextWrite()
        assertEquals("POST /api/v1/plans/$MONDAY/entries", planned)
        assertEquals(100.0, body!!["servings"]!!.jsonPrimitive.content.toDouble())
        val food = body["food"]!!.jsonObject
        assertEquals("dragon-fruit-x1", food["catalogSlug"]!!.jsonPrimitive.content)
        assertEquals("g", food["unit"]!!.jsonPrimitive.content)
        model.settled()
        assertEquals(PlanRoute.Week, model.route.value)
        assertNull(model.picker.value.adding)
    }

    @Test
    fun `a new ingredient the API refuses keeps the panel open with its reason`() = runTest {
        val server = Server()
        server.failIngredient = true
        val model = model(server)
        model.ensureLoaded()
        model.loaded()
        model.openPicker(LocalDate(2026, 11, 16), MealType.LUNCH)
        model.search("Dragon fruit")
        model.picker.first { it.recipes != null }
        model.startNewIngredient()
        model.editNewIngredient {
            it.copy(calories = "60", proteinG = "1", carbsG = "13", fatG = "0")
        }

        model.saveNewIngredient()

        val refused = model.picker.first { it.adding?.error != null }.adding!!
        assertEquals("Name is taken.", refused.error)
        assertTrue(!refused.saving)
        assertEquals("POST /api/v1/ingredients", server.nextWrite().first)
        assertTrue(server.writes.tryReceive().isFailure, "nothing is planned")
        assertIs<PlanRoute.Pick>(model.route.value)
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
    fun `a meal eaten alone logs all its servings in one tap`() = runTest {
        val server = Server().apply { entries += recipeEntry("e1", "DINNER", 2.0, ME) }
        val model = model(server)
        model.ensureLoaded()
        val entry = model.loaded().entries.single()
        model.openMeal(entry)

        model.startLog(entry)

        val (call, body) = server.nextWrite()
        assertEquals("POST /api/v1/logs", call)
        assertEquals("r-e1", body!!["recipeId"]!!.jsonPrimitive.content)
        assertEquals(2.0, body["servings"]!!.jsonPrimitive.content.toDouble())
        assertEquals(Logged("e1", "Stew e1 logged."), model.logged.first { it != null })
        assertEquals(null, model.sharing.value)
    }

    @Test
    fun `a shared meal starts at the caller's share and logs what they type, once it is one`() =
        runTest {
            val shared =
                recipeEntry("e1", "DINNER", 3.0, ME, PARTNER)
                    .replace("}]}", "}],\"yourShare\":1.5}")
            val server =
                Server().apply {
                    members = listOf(ME, PARTNER)
                    entries += shared
                }
            val model = model(server)
            model.ensureLoaded()
            val entry = model.loaded().entries.single()
            model.openMeal(entry)

            model.startLog(entry)
            assertEquals(Sharing("e1", "1.5"), model.sharing.value)
            model.editShare("0.05")
            model.logShare(entry)
            model.editShare("1.2")
            model.logShare(entry)

            val (call, body) = server.nextWrite()
            assertEquals("POST /api/v1/logs", call)
            assertEquals(1.2, body!!["servings"]!!.jsonPrimitive.content.toDouble())
            model.logged.first { it != null }
            assertEquals(null, model.sharing.value)
        }

    @Test
    fun `a planned food logs its amount with the nutrition its ingredient gives it`() = runTest {
        val banana =
            """{"id":"f1","date":"$MONDAY","mealType":"SNACK","servings":2,
                "food":{"catalogSlug":"banana","name":"Banana"},"diners":[$ME],"yourShare":2}"""
        val server = Server().apply { entries += banana }
        val model = model(server)
        model.ensureLoaded()
        val entry = model.loaded().entries.single()

        model.startLog(entry)

        val (_, body) = server.nextWrite()
        assertEquals("Banana, 2 pieces", body!!["description"]!!.jsonPrimitive.content)
        assertEquals(
            210.0,
            body["nutrition"]!!.jsonObject["calories"]!!.jsonPrimitive.content.toDouble(),
        )
    }

    @Test
    fun `a scanned product becomes the household's ingredient, planned at a serving`() = runTest {
        val server =
            Server().apply {
                product =
                    """{"barcode":"3017620422003","name":"Nutella","brand":"Ferrero",
                        "basis":"PER_100_G","servingSize":15,
                        "nutrition":{"calories":539,"proteinG":6.3,"carbsG":57.5,"fatG":30.9}}"""
            }
        val model = model(server)
        model.ensureLoaded()
        model.loaded()
        model.openPicker(LocalDate(2026, 11, 17), MealType.SNACK)

        model.scanner.open()
        model.scanner.type("3017620422003")
        model.scanner.lookUpTyped()

        val (created, ingredient) = server.nextWrite()
        assertEquals("POST /api/v1/ingredients", created)
        assertEquals("Nutella (Ferrero)", ingredient!!["name"]!!.jsonPrimitive.content)
        val (planned, entry) = server.nextWrite()
        assertEquals("POST /api/v1/plans/$MONDAY/entries", planned)
        assertEquals(
            "dragon-fruit-x1",
            entry!!["food"]!!.jsonObject["catalogSlug"]!!.jsonPrimitive.content,
        )
        assertEquals(15.0, entry["servings"]!!.jsonPrimitive.content.toDouble())
        model.route.first { it == PlanRoute.Week }
    }

    @Test
    fun `a scanned product missing a value opens the new ingredient with what is known`() =
        runTest {
            val server =
                Server().apply {
                    product =
                        """{"barcode":"50184453","name":"Yeast Extract","brand":"Marmite",
                            "basis":"PER_100_G","servingSize":8,"nutrition":{"calories":260}}"""
                }
            val model = model(server)
            model.ensureLoaded()
            model.loaded()
            model.openPicker(LocalDate(2026, 11, 17), MealType.SNACK)

            model.scanner.open()
            model.scanner.type("50184453")
            model.scanner.lookUpTyped()

            val adding = model.picker.first { it.adding != null }.adding!!
            assertEquals("Yeast Extract (Marmite)", adding.name)
            assertEquals("260", adding.calories)
            assertEquals("", adding.fatG)
            assertTrue(server.writes.tryReceive().isFailure)
        }

    @Test
    fun `the week reads the caller's fuel for its seven days, with targets while they are on`() =
        runTest {
            val server = Server().apply { targets = mapOf(THURSDAY to 2650) }
            val model = model(server)

            model.ensureLoaded()
            val week = model.loaded()

            assertEquals(
                LocalDate(2026, 11, 16) to LocalDate(2026, 11, 22),
                server.fuelRead.receive(),
            )
            assertTrue(week.targetsOn)
            assertEquals(2650, week.fuelOf(THURSDAY)?.target?.calories)
            assertNull(week.fuelOf(LocalDate(2026, 11, 18))?.target)
            assertEquals(500.0, week.total(THURSDAY)?.total?.calories)
        }

    @Test
    fun `training is added to the open day in whole minutes, and the week reloads`() = runTest {
        val server = Server().apply { targets = mapOf(THURSDAY to 2650) }
        val model = model(server)
        model.ensureLoaded()
        model.loaded()
        server.fuelRead.receive()
        model.openDay(THURSDAY)

        model.startTraining()
        model.editTraining { it.copy(minutes = "4.5") }
        model.addTraining(THURSDAY)
        assertEquals(true, model.training.value?.minutesError)
        assertTrue(server.writes.tryReceive().isFailure, "nothing is sent")

        model.editTraining { it.copy(minutes = "45") }
        assertEquals(false, model.training.value?.minutesError, "typing clears the error")
        model.addTraining(THURSDAY)

        val (write, body) = server.nextWrite()
        assertEquals("POST /api/v1/me/training", write)
        assertEquals("2026-11-19", body!!["date"]!!.jsonPrimitive.content)
        assertEquals("RIDE", body["sport"]!!.jsonPrimitive.content)
        assertEquals("45", body["durationMinutes"]!!.jsonPrimitive.content)
        assertEquals("ENDURANCE", body["intensity"]!!.jsonPrimitive.content)
        model.settled()
        assertNull(model.training.value)
        assertEquals(LocalDate(2026, 11, 16) to LocalDate(2026, 11, 22), server.fuelRead.receive())
        assertEquals(PlanRoute.Day(THURSDAY), model.route.value)
    }

    @Test
    fun `minutes the API refuses mark the field, and a refused removal says so`() = runTest {
        val server =
            Server().apply {
                targets = mapOf(THURSDAY to 2650)
                sessions = mapOf(THURSDAY to listOf(sessionJson("s1", THURSDAY, 60)))
                failMinutes = true
            }
        val model = model(server)
        model.ensureLoaded()
        val session = model.loaded().fuelOf(THURSDAY)!!.sessions.single()
        model.openDay(THURSDAY)

        model.startTraining()
        model.editTraining { it.copy(minutes = "2000") }
        model.addTraining(THURSDAY)
        server.nextWrite()
        model.settled()
        assertEquals(true, model.training.value?.minutesError)
        assertNull(model.error.value, "the field says what to fix")

        server.failWrites = true
        model.removeTraining(session)
        assertEquals("DELETE /api/v1/me/training/s1", server.nextWrite().first)
        model.settled()
        assertEquals(TrainingFailures.REMOVE, model.error.value)
    }

    @Test
    fun `a fuel change reloads an opened week, and leaves an unopened one to load when opened`() =
        runTest {
            val server = Server()
            val model = model(server)
            model.fuelChanged()
            assertTrue(server.fuelRead.tryReceive().isFailure, "never opened")

            model.ensureLoaded()
            model.loaded()
            server.fuelRead.receive()
            model.fuelChanged()
            server.fuelRead.receive()
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
                plugins = { MimosHttp(server.engine, API).api(::PluginsApi) },
                logging = { MimosHttp(server.engine, API).api(::LoggingApi) },
                foodProducts = { MimosHttp(server.engine, API).api(::FoodProductsApi) },
                fuel = { MimosHttp(server.engine, API).api(::FuelApi) },
                today = { THURSDAY },
            )

        failing.ensureLoaded()

        assertIs<WeekState.Failed>(failing.week.first { it is WeekState.Failed })
    }

    @Test
    fun `the week's suggestions come with it, and their failure only empties them`() = runTest {
        val server = Server().apply { suggestions = "[${suggestion("a", "b")}]" }
        val model = model(server)
        model.ensureLoaded()
        assertEquals("Italy week", model.loaded().suggestions.single().title)

        server.failSuggestions = true
        model.nextWeek()
        val next =
            (model.week.first { it is WeekState.Loaded && it.week.monday.day == 23 }
                    as WeekState.Loaded)
                .week
        assertEquals(emptyList(), next.suggestions)
    }

    @Test
    fun `applying a suggestion adds each meal through the plan, counting those added`() = runTest {
        val server = Server().apply { suggestions = "[${suggestion("a", "b", "c")}]" }
        val model = model(server)
        model.ensureLoaded()
        val card = model.loaded().suggestions.single()
        server.failRecipes = setOf("b")

        model.applySuggestion(card)

        for (recipe in listOf("a", "b", "c")) {
            val (call, body) = server.nextWrite()
            assertEquals("POST /api/v1/plans/$MONDAY/entries", call)
            assertEquals(recipe, body!!["recipeId"]!!.jsonPrimitive.content)
            assertEquals("DINNER", body["mealType"]!!.jsonPrimitive.content)
            assertEquals(2.0, body["servings"]!!.jsonPrimitive.content.toDouble())
            assertTrue("diners" !in body, "the API gives dinner every member")
        }
        model.settled()
        assertEquals("Added 2 meals from \"Italy week\".", model.notice.value)
        assertNull(model.error.value)
        assertEquals(false, model.applying.value)

        model.openDay(LocalDate(2026, 11, 16))
        assertNull(model.notice.value, "the notice lasts until the next screen")
    }

    @Test
    fun `a suggestion none of whose meals could be added says so`() = runTest {
        val server = Server().apply { suggestions = "[${suggestion("a")}]" }
        val model = model(server)
        model.ensureLoaded()
        server.failRecipes = setOf("a")

        model.applySuggestion(model.loaded().suggestions.single())
        server.nextWrite()
        model.settled()

        assertEquals("Could not add that suggestion.", model.notice.value)
    }
}

/** A Country of the Week card of dinners, one a day from Monday, for each recipe id. */
private fun suggestion(vararg recipeIds: String): String {
    val entries = recipeIds.mapIndexed { day, id ->
        """{"date":"2026-11-${16 + day}","mealType":"DINNER","recipeId":"$id",
                "recipeTitle":"Dish $id","servings":2}"""
    }
    return """{"pluginId":"country-week","pluginName":"Country of the Week","title":"Italy week",
        "icon":"🇮🇹","entries":[${entries.joinToString(",")}]}"""
}
