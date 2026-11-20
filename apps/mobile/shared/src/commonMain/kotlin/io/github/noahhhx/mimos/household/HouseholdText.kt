package io.github.noahhhx.mimos.household

import io.github.noahhhx.mimos.shell.monthNames
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Names as a sentence lists them: "Sam", "Sam and Alex", "Sam, Alex and Jo". */
fun listNames(names: List<String>): String =
    if (names.size <= 1) names.firstOrNull().orEmpty()
    else names.dropLast(1).joinToString(", ") + " and " + names.last()

/** A household as its members' names: "Sam and Alex's household". */
fun householdName(names: List<String>): String =
    if (names.isEmpty()) "this household" else "${listNames(names)}'s household"

/** "26 November 2026, 21:04": when an invite stops working, in the phone's time zone. */
fun dateTimeText(at: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = at.toLocalDateTime(zone)
    val time =
        "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    return "${local.day} ${monthNames[local.month.ordinal]} ${local.year}, $time"
}
