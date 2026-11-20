package io.github.noahhhx.mimos.recipes

import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private class Sketch(
    val id: String,
    val title: String,
    val tags: List<String> = emptyList(),
    val createdAt: String = "2025-01-01T00:00:00Z",
    val updatedAt: String = "2025-01-01T00:00:00Z",
) {
    fun toSummary() =
        RecipeSummary(
            id = id,
            title = title,
            description = "",
            servings = 1,
            isLibrary = false,
            createdAt = Instant.parse(createdAt),
            updatedAt = Instant.parse(updatedAt),
            tags = tags,
            nutrition = Nutrition(),
        )
}

@Serializable
private class OrderCase(
    val name: String,
    val recipes: List<Sketch>,
    val sort: String,
    val tags: List<String>,
    val expected: List<String>,
)

@Serializable
private class TagCase(val name: String, val recipes: List<Sketch>, val expected: List<String>)

@Serializable private class SortKey(val key: String?, val expected: String)

@Serializable
private class Cases(
    val orderCases: List<OrderCase>,
    val tagCases: List<TagCase>,
    val sortKeys: List<SortKey>,
)

private val cases = Json.decodeFromString<Cases>(TestCases.read("recipe-order.json"))

/** The cases are the web's too (`libraries/test-cases`): its tests run them through `Intl`. */
class RecipeOrderTest {
    @Test
    fun `every shared ordering case gives the expected ids`() {
        assertEquals(true, cases.orderCases.isNotEmpty())
        for (case in cases.orderCases) {
            val ordered =
                orderRecipes(
                    case.recipes.map { it.toSummary() },
                    RecipeSort.parse(case.sort),
                    case.tags.toSet(),
                )
            assertEquals(case.expected, ordered.map { it.id }, case.name)
        }
    }

    @Test
    fun `every shared tag case lists the expected tags`() {
        assertEquals(true, cases.tagCases.isNotEmpty())
        for (case in cases.tagCases) {
            assertEquals(case.expected, recipeTags(case.recipes.map { it.toSummary() }), case.name)
        }
    }

    @Test
    fun `every shared sort key parses as expected`() {
        for (case in cases.sortKeys) {
            assertEquals(RecipeSort.parse(case.expected), RecipeSort.parse(case.key), "${case.key}")
        }
    }

    @Test
    fun `the sorts carry the web's keys and labels`() {
        assertEquals(
            listOf(
                "title" to "Title, A to Z",
                "newest" to "Newest first",
                "oldest" to "Oldest first",
                "edited" to "Recently edited",
            ),
            RecipeSort.entries.map { it.key to it.label },
        )
        assertEquals(RecipeSort.Title, RecipeSort.default)
    }

    @Test
    fun `ordering returns a new list and leaves its input alone`() {
        val input =
            listOf(Sketch("2", "banana bread"), Sketch("1", "Apple tart")).map { it.toSummary() }

        val output = orderRecipes(input, RecipeSort.Title, emptySet())

        assertEquals(listOf("1", "2"), output.map { it.id })
        assertEquals(listOf("2", "1"), input.map { it.id })
    }

    @Test
    fun `a selected tag the tab no longer offers is not applied`() {
        assertEquals(setOf("quick"), activeTags(listOf("quick", "soup"), setOf("quick", "baking")))
        assertEquals(emptySet(), activeTags(emptyList(), setOf("quick")))
    }
}
