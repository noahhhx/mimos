package io.github.noahhhx.mimos.kitchen

import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.Person
import io.github.noahhhx.mimos.api.models.PlanSuggestion
import io.github.noahhhx.mimos.api.models.PlanSuggestionEntry
import io.github.noahhhx.mimos.api.models.ShoppingListItem
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
private class KitchenEntry(
    val date: String,
    val mealType: MealType,
    val title: String,
    val diners: String = "mine",
)

@Serializable private class GreetingCase(val hour: Int, val minute: Int, val greeting: String)

@Serializable private class ExpectedTonight(val title: String, val label: String)

@Serializable
private class TonightCase(
    val about: String,
    val today: String,
    val entries: List<KitchenEntry>,
    val expected: ExpectedTonight?,
)

@Serializable private class ExpectedDinner(val date: String, val title: String?)

@Serializable
private class WeekCase(
    val about: String,
    val monday: String,
    val entries: List<KitchenEntry>,
    val expected: List<ExpectedDinner>,
)

@Serializable private class KitchenCard(val title: String, val dates: List<String>)

@Serializable private class ExpectedThought(val title: String, val date: String)

@Serializable
private class ThoughtCase(
    val about: String,
    val today: String,
    val suggestions: List<KitchenCard>,
    val entries: List<KitchenEntry>,
    val expected: ExpectedThought?,
)

@Serializable private class KitchenItem(val name: String, val checked: Boolean = false)

@Serializable
private class ExpectedToBuy(val shown: List<String>, val remaining: Int, val total: Int)

@Serializable
private class StillToBuyCase(
    val about: String,
    val limit: Int? = null,
    val items: List<KitchenItem>,
    val expected: ExpectedToBuy,
)

@Serializable
private class MinutesCase(
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val expected: Int?,
)

@Serializable
private class KitchenCases(
    val greetingCases: List<GreetingCase>,
    val tonightCases: List<TonightCase>,
    val weekCases: List<WeekCase>,
    val thoughtCases: List<ThoughtCase>,
    val stillToBuyCases: List<StillToBuyCase>,
    val totalMinutesCases: List<MinutesCase>,
)

private val cases = MimosJson.decodeFromString<KitchenCases>(TestCases.read("kitchen.json"))

private val me = Person("me", "Sam", you = true)
private val them = Person("them", "Alex", you = false)

private fun KitchenEntry.entry() =
    MealPlanEntry(
        id = "$date:$mealType:$title",
        date = LocalDate.parse(date),
        mealType = mealType,
        servings = 2.0,
        diners =
            when (diners) {
                "theirs" -> listOf(them)
                "shared" -> listOf(me, them)
                else -> listOf(me)
            },
        recipeId = title.lowercase(),
        recipeTitle = title,
    )

private fun KitchenCard.card() =
    PlanSuggestion(
        pluginId = "country-week",
        pluginName = "Country of the Week",
        title = title,
        propertyEntries =
            dates.map {
                PlanSuggestionEntry(
                    LocalDate.parse(it),
                    MealType.DINNER,
                    "mujadara",
                    "Mujadara",
                    2.0,
                )
            },
    )

class KitchenRulesTest {
    @Test
    fun `the greeting follows the hour as on the web`() {
        for (case in cases.greetingCases) {
            assertEquals(case.greeting, greeting(case.hour), "${case.hour}:${case.minute}")
        }
    }

    @Test
    fun `tonight's meal is picked as on the web`() {
        for (case in cases.tonightCases) {
            val picked = pickTonight(case.entries.map { it.entry() }, LocalDate.parse(case.today))
            assertEquals(
                case.expected?.let { it.title to it.label },
                picked?.let { it.entry.recipeTitle to it.label.text },
                case.about,
            )
        }
    }

    @Test
    fun `the week's dinners are listed as on the web`() {
        for (case in cases.weekCases) {
            val week = weekDinners(case.entries.map { it.entry() }, LocalDate.parse(case.monday))
            assertEquals(
                case.expected.map { LocalDate.parse(it.date) to it.title },
                week.map { it.date to it.dinner?.recipeTitle },
                case.about,
            )
        }
    }

    @Test
    fun `a thought fills an open evening as on the web`() {
        for (case in cases.thoughtCases) {
            val thought =
                pickThought(
                    case.suggestions.map { it.card() },
                    case.entries.map { it.entry() },
                    LocalDate.parse(case.today),
                )
            assertEquals(
                case.expected?.let { it.title to LocalDate.parse(it.date) },
                thought?.let { it.suggestion.title to it.entry.date },
                case.about,
            )
        }
    }

    @Test
    fun `still to buy shows what is left first as on the web`() {
        for (case in cases.stillToBuyCases) {
            val items =
                case.items.map { ShoppingListItem(it.name, it.name, "Pantry", it.checked, false) }
            val result = case.limit?.let { stillToBuy(items, it) } ?: stillToBuy(items)
            assertEquals(
                Triple(case.expected.shown, case.expected.remaining, case.expected.total),
                Triple(result.shown.map { it.name }, result.remaining, result.total),
                case.about,
            )
        }
    }

    @Test
    fun `total minutes add whichever is known as on the web`() {
        for (case in cases.totalMinutesCases) {
            assertEquals(case.expected, totalMinutes(case.prepMinutes, case.cookMinutes))
        }
    }

    @Test
    fun `the date reads in full`() {
        assertEquals("Friday 20 November", longDateLabel(LocalDate(2026, 11, 20)))
        assertEquals("Monday 1 June", longDateLabel(LocalDate(2026, 6, 1)))
    }
}
