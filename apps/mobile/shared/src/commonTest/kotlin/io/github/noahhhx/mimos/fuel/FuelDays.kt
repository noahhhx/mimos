package io.github.noahhhx.mimos.fuel

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** A target as the API answers it, with [calories] and fixed grams. */
fun targetJson(calories: Int): String =
    """{"calories":$calories,"proteinG":120,"carbsG":350,"fatG":80,
        "basis":{"energyAvailabilityKcalPerKgFfm":45,"bodyFatPercent":20,
        "bodyFatSource":"DEFAULT","fatFreeMassKg":56,"exerciseKcal":0,"sessions":[],
        "loadBand":"LIGHT","carbsLeftBand":false}}"""

/** A manual session as the API answers it. */
fun sessionJson(id: String, date: LocalDate, minutes: Int, source: String = "MANUAL"): String =
    """{"id":"$id","date":"$date","source":"$source","kind":"PLANNED","sport":"RIDE",
        "durationMinutes":$minutes,"intensity":"ENDURANCE"}"""

/**
 * `GET /api/v1/me/fuel` from [from] to [to]: each day [planned] calories over one meal (none when
 * null), its target from [targets], and its sessions from [sessions].
 */
fun fuelDaysJson(
    from: LocalDate,
    to: LocalDate,
    planned: Double? = null,
    targets: Map<LocalDate, Int> = emptyMap(),
    sessions: Map<LocalDate, List<String>> = emptyMap(),
): String {
    val days =
        generateSequence(from) { it.plus(DatePeriod(days = 1)) }
            .takeWhile { it <= to }
            .joinToString(",") { day ->
                val total = planned?.let { """{"calories":$it}""" } ?: "{}"
                val meals = if (planned == null) 0 else 1
                val target = targets[day]?.let { ""","target":${targetJson(it)}""" } ?: ""
                """{"date":"$day","planned":{"date":"$day","total":$total,"meals":$meals,
                    "mealsWithoutNutrition":0},"logged":{},
                    "sessions":[${sessions[day].orEmpty().joinToString(",")}]$target}"""
            }
    return """{"from":"$from","to":"$to","days":[$days]}"""
}
