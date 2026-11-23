package io.github.noahhhx.mimos.log

import io.github.noahhhx.mimos.api.models.Nutrition
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The line over a day's date in the Log's pager. */
fun dayEyebrow(day: LocalDate, today: LocalDate): String =
    when (day) {
        today -> "Today"
        today.minus(DatePeriod(days = 1)) -> "Yesterday"
        today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        else -> day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    }

/**
 * One of a day's four totals: what was logged, and the day's target beside it while targets are on.
 * [share] fills the bar and stops at a full bar: a target is information, never a score.
 */
data class DayTotal(val label: String, val value: String, val of: String?, val share: Float?)

/** The day's calories, protein, carbs, and fat, each against its target when there is one. */
fun dayTotals(eaten: Nutrition?, target: Nutrition?): List<DayTotal> {
    val values = bareCells(eaten)
    val targets = target?.let(::bareCells)
    fun amounts(n: Nutrition?) = listOf(n?.calories, n?.proteinG, n?.carbsG, n?.fatG)
    val eatenAmounts = amounts(eaten)
    val targetAmounts = amounts(target)
    return listOf("Kcal", "Protein", "Carbs", "Fat").mapIndexed { i, label ->
        val goal = targetAmounts[i]?.takeIf { it > 0 }
        DayTotal(
            label,
            values[i],
            of = targets?.get(i),
            share = goal?.let { ((eatenAmounts[i] ?: 0.0) / it).coerceIn(0.0, 1.0).toFloat() },
        )
    }
}

/** [nutritionCells] under headings that name each unit: calories as a bare number. */
fun bareCells(nutrition: Nutrition?): List<String> =
    nutritionCells(nutrition).map { it.removeSuffix(" kcal") }
