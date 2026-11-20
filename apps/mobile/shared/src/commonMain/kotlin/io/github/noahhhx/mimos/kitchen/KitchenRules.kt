package io.github.noahhhx.mimos.kitchen

import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.PlanSuggestion
import io.github.noahhhx.mimos.api.models.PlanSuggestionEntry
import io.github.noahhhx.mimos.api.models.ShoppingListItem
import io.github.noahhhx.mimos.plan.eatenByYou
import io.github.noahhhx.mimos.plan.weekDays
import io.github.noahhhx.mimos.plan.weekdayName
import kotlinx.datetime.LocalDate

/*
 * The Kitchen home's choices, ported from the web's `apps/web/src/lib/kitchen.ts`: pure functions
 * of the week's data and the phone's time. Its meals are the caller's: in a shared household, a
 * meal planned only for someone else is never "tonight" (ADR-0019).
 * `libraries/test-cases/kitchen.json` holds the cases both sides run.
 */

/** "Good morning." 05:00–11:59, "Good afternoon." 12:00–17:59, "Good evening." otherwise. */
fun greeting(hour: Int): String =
    when (hour) {
        in 5..11 -> "Good morning."
        in 12..17 -> "Good afternoon."
        else -> "Good evening."
    }

private val months =
    listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )

/** "Friday 20 November": the date above the greeting. */
fun longDateLabel(date: LocalDate): String =
    "${weekdayName(date)} ${date.day} ${months[date.month.ordinal]}"

/** The heading today's meal goes under. */
enum class TonightLabel(val text: String) {
    Tonight("Tonight"),
    Today("Today"),
}

data class TonightPick(val entry: MealPlanEntry, val label: TonightLabel)

/** When today has no dinner, the meal shown under "Today": the latest main meal first. */
private val TODAY_FALLBACK = listOf(MealType.LUNCH, MealType.BREAKFAST, MealType.SNACK)

/**
 * Today's first dinner the caller eats; failing that, today's other meal they eat (lunch, then
 * breakfast, then a snack) under "Today"; null when they have nothing planned.
 */
fun pickTonight(entries: List<MealPlanEntry>, today: LocalDate): TonightPick? {
    val todays = entries.filter { it.date == today && it.eatenByYou() }
    todays
        .find { it.mealType == MealType.DINNER }
        ?.let {
            return TonightPick(it, TonightLabel.Tonight)
        }
    for (mealType in TODAY_FALLBACK) {
        todays
            .find { it.mealType == mealType }
            ?.let {
                return TonightPick(it, TonightLabel.Today)
            }
    }
    return null
}

data class DayDinner(val date: LocalDate, val dinner: MealPlanEntry?)

/** Monday to Sunday, each day with its first dinner the caller eats, or none. */
fun weekDinners(entries: List<MealPlanEntry>, monday: LocalDate): List<DayDinner> =
    weekDays(monday).map { date ->
        DayDinner(
            date,
            entries.find { it.date == date && it.mealType == MealType.DINNER && it.eatenByYou() },
        )
    }

/** A plugin's card and the one proposed dinner the home offers from it. */
data class Thought(val suggestion: PlanSuggestion, val entry: PlanSuggestionEntry)

/**
 * The first card that fills an open dinner from today on: its first dinner entry on a day with no
 * dinner planned. Open means open for the household, since a suggested dinner is for everyone.
 */
fun pickThought(
    suggestions: List<PlanSuggestion>,
    entries: List<MealPlanEntry>,
    today: LocalDate,
): Thought? {
    val planned = entries.filter { it.mealType == MealType.DINNER }.mapTo(HashSet()) { it.date }
    for (suggestion in suggestions) {
        val entry =
            suggestion.propertyEntries.find {
                it.mealType == MealType.DINNER && it.date >= today && it.date !in planned
            }
        if (entry != null) return Thought(suggestion, entry)
    }
    return null
}

data class StillToBuy(val shown: List<ShoppingListItem>, val remaining: Int, val total: Int)

/** Up to [limit] rows, unchecked items first, checked ones filling what is left. */
fun stillToBuy(items: List<ShoppingListItem>, limit: Int = 5): StillToBuy {
    val (checked, unchecked) = items.partition { it.checked }
    return StillToBuy((unchecked + checked).take(limit), unchecked.size, items.size)
}

/** Prep plus cook time, whichever is known; null when neither is. */
fun totalMinutes(prepMinutes: Int?, cookMinutes: Int?): Int? =
    if (prepMinutes == null && cookMinutes == null) null
    else (prepMinutes ?: 0) + (cookMinutes ?: 0)
