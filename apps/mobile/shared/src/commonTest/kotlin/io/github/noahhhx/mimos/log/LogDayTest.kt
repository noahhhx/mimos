package io.github.noahhhx.mimos.log

import io.github.noahhhx.mimos.api.models.Nutrition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

private val MONDAY = LocalDate(2026, 11, 23)

class LogDayTest {
    @Test
    fun `the pager says Today only on today, and names any other day truthfully`() {
        assertEquals("Today", dayEyebrow(MONDAY, MONDAY))
        assertEquals("Yesterday", dayEyebrow(LocalDate(2026, 11, 22), MONDAY))
        assertEquals("Tomorrow", dayEyebrow(LocalDate(2026, 11, 24), MONDAY))
        assertEquals("Thursday", dayEyebrow(LocalDate(2026, 11, 19), MONDAY))
    }

    @Test
    fun `each total sits against its target, and a bar stops full however much was logged`() {
        val totals =
            dayTotals(Nutrition(1116.0, 61.0, 360.0, 34.0), Nutrition(2400.0, 140.0, 300.0, 80.0))
        assertEquals(
            listOf(
                DayTotal("Kcal", "1116", "2400", 0.465f),
                DayTotal("Protein", "61 g", "140 g", 61f / 140),
                DayTotal("Carbs", "360 g", "300 g", 1f),
                DayTotal("Fat", "34 g", "80 g", 34f / 80),
            ),
            totals,
        )
    }

    @Test
    fun `without a target the totals are plain figures, and nothing logged is a dash`() {
        assertEquals(
            listOf(
                DayTotal("Kcal", "–", null, null),
                DayTotal("Protein", "–", null, null),
                DayTotal("Carbs", "–", null, null),
                DayTotal("Fat", "–", null, null),
            ),
            dayTotals(null, null),
        )
    }
}
