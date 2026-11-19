package io.github.noahhhx.mimos.log

import io.github.noahhhx.mimos.api.apis.LoggingApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.DailyLogSummary
import io.github.noahhhx.mimos.api.models.MealLog
import io.github.noahhhx.mimos.api.models.MealLogInput
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionBasis
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
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer

private const val API = "https://api.mimos.example.com"
private val THURSDAY = LocalDate(2026, 11, 19)
private val MONDAY = LocalDate(2026, 11, 16)

private val SUGAR =
    CatalogIngredient(
        "sugar",
        "Sugar",
        isShared = true,
        basis = NutritionBasis.PER_100_G,
        nutrition = Nutrition(400.0, 0.0, 100.0, 0.0),
    )

private val STEW =
    RecipeSummary(
        "r-stew",
        "Stew",
        "",
        4,
        isLibrary = false,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
        tags = emptyList(),
        nutrition = Nutrition(500.0),
    )

/** A person's log, kept as the API would, with what each list read and write was sent. */
private class LogServer {
    val logs = mutableListOf<MealLog>()
    val created = mutableListOf<MealLogInput>()
    val reads = mutableListOf<Pair<String, String>>()
    var failWrites = false
    var failCatalog = false
    var catalogReads = 0

    val engine = MockEngine { request ->
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val path = request.url.encodedPath
        fun range(): Pair<LocalDate, LocalDate> =
            LocalDate.parse(request.url.parameters["from"]!!) to
                LocalDate.parse(request.url.parameters["to"]!!)
        when {
            request.method == HttpMethod.Get && path == "/api/v1/logs" -> {
                val (from, to) = range()
                reads += from.toString() to to.toString()
                val week = logs.filter { it.date in from..to }
                respond(
                    MimosJson.encodeToString(ListSerializer(MealLog.serializer()), week),
                    headers = json,
                )
            }
            path == "/api/v1/logs/summary" -> {
                val (from, to) = range()
                val days =
                    logs
                        .filter { it.date in from..to }
                        .groupBy { it.date }
                        .map { (date, day) ->
                            DailyLogSummary(
                                date,
                                day.sumOf { it.nutrition.calories ?: 0.0 },
                                day.sumOf { it.nutrition.proteinG ?: 0.0 },
                                day.sumOf { it.nutrition.carbsG ?: 0.0 },
                                day.sumOf { it.nutrition.fatG ?: 0.0 },
                            )
                        }
                respond(
                    MimosJson.encodeToString(ListSerializer(DailyLogSummary.serializer()), days),
                    headers = json,
                )
            }
            request.method == HttpMethod.Post && path == "/api/v1/logs" -> {
                if (failWrites) return@MockEngine respondError(HttpStatusCode.BadRequest)
                val input =
                    MimosJson.decodeFromString(
                        MealLogInput.serializer(),
                        (request.body as TextContent).text,
                    )
                created += input
                val log =
                    MealLog(
                        "log-${logs.size + 1}",
                        input.date,
                        input.mealType,
                        input.description ?: "A recipe",
                        input.servings ?: 1.0,
                        input.nutrition ?: Nutrition(),
                        input.recipeId,
                    )
                logs += log
                respond(MimosJson.encodeToString(MealLog.serializer(), log), headers = json)
            }
            request.method == HttpMethod.Delete -> {
                logs.removeAll { it.id == path.substringAfterLast('/') }
                respond("", HttpStatusCode.NoContent)
            }
            path == "/api/v1/ingredients" -> {
                catalogReads++
                if (failCatalog) respondError(HttpStatusCode.BadGateway)
                else
                    respond(
                        MimosJson.encodeToString(
                            ListSerializer(CatalogIngredient.serializer()),
                            listOf(SUGAR),
                        ),
                        headers = json,
                    )
            }
            path == "/api/v1/recipes" ->
                respond(
                    MimosJson.encodeToString(
                        ListSerializer(RecipeSummary.serializer()),
                        listOf(STEW),
                    ),
                    headers = json,
                )
            else -> respond("[]", headers = json)
        }
    }
}

private fun TestScope.model(server: LogServer) =
    LogModel(
        this,
        logging = { MimosHttp(server.engine, API).api(::LoggingApi) },
        recipes = { MimosHttp(server.engine, API).api(::RecipesApi) },
        today = { THURSDAY },
    )

private suspend fun LogModel.loaded(monday: LocalDate = MONDAY): LogWeek =
    (week.first { it is LogWeekState.Loaded && it.week.monday == monday } as LogWeekState.Loaded)
        .week

class LogModelTest {
    @Test
    fun `the log opens on today in its week, and reads the week again each time it opens`() =
        runTest {
            val server = LogServer()
            val model = model(server)

            model.opened()
            model.loaded()
            assertEquals(THURSDAY, model.selected.value)
            assertEquals(listOf("2026-11-16" to "2026-11-22"), server.reads)

            server.logs +=
                MealLog("planned", THURSDAY, MealType.DINNER, "Stew", 1.0, Nutrition(500.0))
            model.opened()
            val week =
                model.week.first {
                    it is LogWeekState.Loaded && it.week.logs.isNotEmpty()
                } as LogWeekState.Loaded
            assertEquals(listOf("Stew"), week.week.logsOn(THURSDAY).map { it.description })
            assertEquals(500.0, week.week.summaryOf(THURSDAY)?.calories)
        }

    @Test
    fun `a picked ingredient logs as the web logs it on the selected day, and the form clears`() =
        runTest {
            val server = LogServer()
            val model = model(server)
            model.opened()
            model.loaded()
            val choices = model.choices.first { it.ingredients.isNotEmpty() }

            model.selectDay(LocalDate(2026, 11, 17))
            model.type("sug")
            val option = choices.search(whatText(model.form.value.what)).single()
            model.pick(option)
            assertEquals(AmountFields("100", "g"), model.form.value.amount)
            model.editAmount { it.copy(quantity = "15") }
            model.add()
            model.adding.first { !it }

            assertEquals(
                listOf(
                    MealLogInput(
                        LocalDate(2026, 11, 17),
                        MealType.SNACK,
                        description = "Sugar, 15 g",
                        servings = 1.0,
                        nutrition = Nutrition(60.0, 0.0, 15.0, 0.0),
                    )
                ),
                server.created,
            )
            assertEquals(LogWhat.Custom(""), model.form.value.what)
            val week =
                model.week.first {
                    it is LogWeekState.Loaded && it.week.logs.isNotEmpty()
                } as LogWeekState.Loaded
            assertEquals(60.0, week.week.summaryOf(LocalDate(2026, 11, 17))?.calories)
        }

    @Test
    fun `nothing is sent while the form cannot log, and a refused log keeps the form`() = runTest {
        val server = LogServer()
        val model = model(server)
        model.opened()
        model.loaded()

        model.add()
        assertTrue(server.created.isEmpty())

        server.failWrites = true
        model.type("Toast")
        model.editNutrition { it.copy(calories = "310") }
        model.add()
        model.error.first { it != null }
        assertEquals("Could not log that meal.", model.error.value)
        assertEquals(LogWhat.Custom("Toast"), model.form.value.what)
        assertEquals("310", model.form.value.nutrition.calories)
    }

    @Test
    fun `the weeks move with the selected weekday, and a day outside the week is not selected`() =
        runTest {
            val server = LogServer()
            val model = model(server)
            model.opened()
            model.loaded()

            model.previousWeek()
            model.loaded(LocalDate(2026, 11, 9))
            assertEquals(LocalDate(2026, 11, 12), model.selected.value)

            model.selectDay(LocalDate(2026, 11, 20))
            assertEquals(LocalDate(2026, 11, 12), model.selected.value)

            model.thisWeek()
            model.loaded()
            assertEquals(THURSDAY, model.selected.value)
        }

    @Test
    fun `a deleted entry leaves the week`() = runTest {
        val server = LogServer()
        server.logs += MealLog("toast", THURSDAY, MealType.SNACK, "Toast", 1.0, Nutrition(310.0))
        val model = model(server)
        model.opened()
        val log = model.loaded().logs.single()

        model.delete(log)

        model.week.first { it is LogWeekState.Loaded && it.week.logs.isEmpty() }
        assertTrue(server.logs.isEmpty())
    }

    @Test
    fun `a search catalog that failed to load is asked for again the next time the tab opens`() =
        runTest {
            val server = LogServer()
            server.failCatalog = true
            val model = model(server)
            model.opened()
            model.loaded()
            val withoutCatalog = model.choices.first { it.recipes.isNotEmpty() }
            assertEquals(listOf(STEW), withoutCatalog.recipes)
            assertTrue(withoutCatalog.ingredients.isEmpty())

            server.failCatalog = false
            model.opened()
            assertEquals(
                listOf(SUGAR),
                model.choices.first { it.ingredients.isNotEmpty() }.ingredients,
            )

            model.opened()
            assertEquals(2, server.catalogReads)
        }
}
