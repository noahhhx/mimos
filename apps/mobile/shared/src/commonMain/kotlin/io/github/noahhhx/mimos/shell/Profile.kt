package io.github.noahhhx.mimos.shell

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** What the profile menu shows of the person, loaded through the API with the bearer token. */
sealed interface Profile {
    data object Loading : Profile

    data class Known(val displayName: String, val memberSince: Instant) : Profile

    data class Failed(val message: String) : Profile
}

/** The months as the web's dates name them in English. */
internal val monthNames =
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

/** "Member since 18 November 2026": the web's menu line, in the phone's time zone. */
fun memberSinceText(since: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = since.toLocalDateTime(zone)
    return "Member since ${date.day} ${monthNames[date.month.ordinal]} ${date.year}"
}
