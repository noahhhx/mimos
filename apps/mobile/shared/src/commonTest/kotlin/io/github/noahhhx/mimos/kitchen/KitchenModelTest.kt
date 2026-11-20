package io.github.noahhhx.mimos.kitchen

import io.github.noahhhx.mimos.api.apis.PlanningApi
import io.github.noahhhx.mimos.api.apis.PluginsApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.apis.ShoppingListsApi
import io.github.noahhhx.mimos.api.models.MealPlan
import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionSource
import io.github.noahhhx.mimos.api.models.Person
import io.github.noahhhx.mimos.api.models.PlanSuggestion
import io.github.noahhhx.mimos.api.models.PlanSuggestionEntry
import io.github.noahhhx.mimos.api.models.PlanSuggestions
import io.github.noahhhx.mimos.api.models.RecipeDetail
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
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val API = "https://api.mimos.example.com"
private val MONDAY = LocalDate(2026, 11, 16)
private val THURSDAY = LocalDate(2026, 11, 19)
private val me = Person("me", "Sam", you = true)

private fun dinner(date: LocalDate, title: String) =
    MealPlanEntry(
        "$date-$title",
        date,
        MealType.DINNER,
        2.0,
        listOf(me),
        recipeId = title.lowercase(),
        recipeTitle = title,
    )

/** The week as the API would answer it, with every meal the home plans. */
private class KitchenServer {
    var entries = listOf(dinner(THURSDAY, "Dahl"))
    var list: ShoppingList? = null
    var failPlan = false
    val planned = mutableListOf<String>()
    val suggestion =
        PlanSuggestion(
            "country-week",
            "Country of the Week",
            "Italy week",
            listOf(
                PlanSuggestionEntry(THURSDAY, MealType.DINNER, "risotto", "Risotto", 2.0),
                PlanSuggestionEntry(
                    LocalDate(2026, 11, 20),
                    MealType.DINNER,
                    "minestrone",
                    "Minestrone",
                    2.0,
                ),
            ),
        )

    val engine = MockEngine { request ->
        fun json(text: String) =
            respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json"))

        val path = request.url.segments.joinToString("/", prefix = "/")
        when {
            path.endsWith("/suggestions") ->
                json(
                    MimosJson.encodeToString(
                        PlanSuggestions.serializer(),
                        PlanSuggestions(listOf(suggestion)),
                    )
                )
            path.endsWith("/shopping-list") ->
                list?.let { json(MimosJson.encodeToString(ShoppingList.serializer(), it)) }
                    ?: respondError(HttpStatusCode.NotFound)
            path.startsWith("/api/v1/recipes/") ->
                json(
                    MimosJson.encodeToString(
                        RecipeDetail.serializer(),
                        RecipeDetail(
                            id = path.substringAfterLast('/'),
                            title = "Dahl",
                            description = "Red lentils.",
                            servings = 4,
                            isLibrary = true,
                            tags = emptyList(),
                            nutrition = Nutrition(calories = 512.4),
                            nutritionSource = NutritionSource.INGREDIENTS,
                            ingredients = emptyList(),
                            steps = emptyList(),
                            pictures = emptyList(),
                            prepMinutes = 10,
                            cookMinutes = 30,
                        ),
                    )
                )
            request.method == HttpMethod.Post && path.endsWith("/entries") -> {
                val body =
                    MimosJson.parseToJsonElement((request.body as TextContent).text).jsonObject
                val recipe = body["recipeId"]!!.jsonPrimitive.content
                val date = LocalDate.parse(body["date"]!!.jsonPrimitive.content)
                planned += "$date $recipe"
                val entry = dinner(date, recipe.replaceFirstChar { it.uppercase() })
                entries = entries + entry
                json(MimosJson.encodeToString(MealPlanEntry.serializer(), entry))
            }
            failPlan -> respondError(HttpStatusCode.InternalServerError)
            else -> json(MimosJson.encodeToString(MealPlan.serializer(), MealPlan(MONDAY, entries)))
        }
    }
}

private fun TestScope.model(server: KitchenServer, onPlanned: () -> Unit = {}): KitchenModel {
    fun http() = MimosHttp(server.engine, API)
    return KitchenModel(
        this,
        planning = { http().api(::PlanningApi) },
        plugins = { http().api(::PluginsApi) },
        recipes = { http().api(::RecipesApi) },
        shopping = { http().api(::ShoppingListsApi) },
        now = { LocalDateTime(THURSDAY, LocalTime(18, 30)) },
        onPlanned = onPlanned,
    )
}

private suspend fun KitchenModel.loadedWeek() =
    (week.first { it is Block.Loaded } as Block.Loaded).value

class KitchenModelTest {
    @Test
    fun `opening reads tonight's dinner and its recipe, the thought, and a list never made`() =
        runTest {
            val model = model(KitchenServer())
            model.open()

            val week = model.loadedWeek()
            assertEquals("Dahl", pickTonight(week.entries, model.today)?.entry?.recipeTitle)
            val recipe = (model.recipe.first { it is Block.Loaded } as Block.Loaded).value
            assertEquals(40, totalMinutes(recipe.prepMinutes, recipe.cookMinutes))
            // Thursday has its dinner, so the card's Friday is the open evening.
            assertEquals(
                LocalDate(2026, 11, 20),
                pickThought(week.suggestions, week.entries, model.today)?.entry?.date,
            )
            assertEquals(Block.Loaded(null), model.list.first { it != Block.Loading })
        }

    @Test
    fun `the list shows what is still to buy`() = runTest {
        val server =
            KitchenServer().apply {
                list =
                    ShoppingList(
                        MONDAY,
                        Instant.parse("2026-11-16T09:00:00Z"),
                        listOf(
                            ShoppingListItem(
                                "a",
                                "onion",
                                "Produce",
                                checked = true,
                                added = false,
                            ),
                            ShoppingListItem("b", "milk", "Dairy", checked = false, added = false),
                        ),
                    )
            }
        val model = model(server)
        model.open()

        val list = (model.list.first { it is Block.Loaded } as Block.Loaded).value!!
        assertEquals(StillToBuy(listOf(list.items[1], list.items[0]), 1, 2), stillToBuy(list.items))
    }

    @Test
    fun `a failed plan fails its own block, and the list still loads`() = runTest {
        val model = model(KitchenServer().apply { failPlan = true })
        model.open()

        assertEquals(Block.Failed, model.week.first { it != Block.Loading })
        assertEquals(Block.Loaded(null), model.list.first { it != Block.Loading })
    }

    @Test
    fun `adding the thought plans its one dinner, says so, and tells the plan`() = runTest {
        val server = KitchenServer()
        var told = 0
        val model = model(server) { told++ }
        model.open()
        val week = model.loadedWeek()
        val thought = pickThought(week.suggestions, week.entries, model.today)!!

        model.addThought(thought)

        assertEquals(
            ThoughtNotice("Added Minestrone to Friday.", added = true),
            model.notice.first { it != null },
        )
        assertEquals(listOf("2026-11-20 minestrone"), server.planned)
        assertEquals(1, told)
        // The week is read again, so Friday is no longer open and the card has nothing to offer.
        model.week.first {
            it is Block.Loaded &&
                pickThought(it.value.suggestions, it.value.entries, THURSDAY) == null
        }
    }
}
