package io.github.noahhhx.mimos.fuel

import io.github.noahhhx.mimos.api.models.FuelGoal
import io.github.noahhhx.mimos.api.models.FuelProfile
import io.github.noahhhx.mimos.api.models.FuelProfileInput
import io.github.noahhhx.mimos.api.models.FuelSex
import io.github.noahhhx.mimos.api.models.FuelTarget
import io.github.noahhhx.mimos.api.models.IntervalsIcuConnection
import io.github.noahhhx.mimos.api.models.IntervalsIcuConnectionStatus
import io.github.noahhhx.mimos.api.models.IntervalsIcuHints
import io.github.noahhhx.mimos.api.models.PlannedDayTotal
import io.github.noahhhx.mimos.api.models.TrainingIntensity
import io.github.noahhhx.mimos.api.models.TrainingKind
import io.github.noahhhx.mimos.api.models.TrainingSession
import io.github.noahhhx.mimos.api.models.TrainingSessionInput
import io.github.noahhhx.mimos.api.models.TrainingSource
import io.github.noahhhx.mimos.api.models.TrainingSport
import io.github.noahhhx.mimos.recipes.form.numberText
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// The web's lib/fuel.ts, lib/fuel-fields.ts, and lib/intervals-icu.ts, tested from
// libraries/test-cases/fuel.json.

/** What a day's target is called wherever it shows: information about the day, never a score. */
const val FUEL_LABEL = "Fuel for the day"

const val TRAINING_LABEL = "Training"

/** A goal, and the protein in g per kg it starts the profile at. */
class Goal(val value: FuelGoal, val label: String, val proteinGPerKg: Double)

val GOALS =
    listOf(
        Goal(FuelGoal.FUEL, "Fuel my training", 1.6),
        Goal(FuelGoal.GRADUAL_LOSS, "Lose weight gradually", 2.0),
        Goal(FuelGoal.FASTER_LOSS, "Lose weight faster", 2.2),
        Goal(FuelGoal.GAIN, "Gain weight", 1.8),
    )

val SEXES =
    listOf(
        FuelSex.FEMALE to "Female",
        FuelSex.MALE to "Male",
        FuelSex.UNSPECIFIED to "Prefer not to say",
    )

/** The profile's fields a 400 may name, by the name the API gives them. */
enum class FuelField(val apiName: String, val error: String) {
    WeightKg("weightKg", "Enter a weight from 30 to 250 kg."),
    BodyFatPercent("bodyFatPercent", "Enter body fat from 3 to 60%, or leave it empty."),
    FtpWatts("ftpWatts", "Enter a whole number of watts from 50 to 600, or leave it empty."),
    ProteinGPerKg("proteinGPerKg", "Enter protein from 1.2 to 3.0 g per kg."),
}

const val DURATION_FIELD = "durationMinutes"
const val DURATION_ERROR = "Enter a whole number of minutes from 1 to 1440."

object TrainingFailures {
    const val ADD = "Could not add that training."
    const val REMOVE = "Could not remove that training."
}

object IntervalsIcuFailures {
    const val LOAD = "Could not load your intervals.icu connection."
    const val CONNECT = "Could not connect intervals.icu. Try again."
    const val SYNC = "Could not sync from intervals.icu. Try again."
    const val DISCONNECT = "Could not disconnect intervals.icu. Try again."
}

/** The names among [known] that a 400's detail ("field message; field message") gives. */
fun namedFields(detail: String?, known: Collection<String>): List<String> =
    (detail ?: "").split("; ").map { it.substringBefore(' ') }.filter { it in known }

/** The profile's fields that a 400's detail names. */
fun namedProfileFields(detail: String?): List<FuelField> =
    namedFields(detail, FuelField.entries.map { it.apiName }).map { name ->
        FuelField.entries.first { it.apiName == name }
    }

val SPORTS =
    listOf(
        TrainingSport.RIDE to "Ride",
        TrainingSport.RUN to "Run",
        TrainingSport.SWIM to "Swim",
        TrainingSport.STRENGTH to "Strength",
        TrainingSport.OTHER to "Other",
    )

val INTENSITIES =
    listOf(
        TrainingIntensity.EASY to "Easy",
        TrainingIntensity.ENDURANCE to "Endurance",
        TrainingIntensity.HARD to "Hard",
    )

fun sportLabel(sport: TrainingSport): String =
    SPORTS.firstOrNull { it.first == sport }?.second ?: "Other"

fun intensityLabel(intensity: TrainingIntensity): String? =
    INTENSITIES.firstOrNull { it.first == intensity }?.second

private fun kindLabel(kind: TrainingKind): String =
    if (kind == TrainingKind.COMPLETED) "Completed" else "Planned"

private fun sourceLabel(source: TrainingSource): String =
    if (source == TrainingSource.INTERVALS_ICU) "intervals.icu" else "By hand"

/**
 * "Ride", "90 min", "Endurance", "Planned", "intervals.icu": a session's sport, length, intensity
 * when known, kind, and where it came from.
 */
fun sessionParts(session: TrainingSession): List<String> =
    listOfNotNull(
        sportLabel(session.sport),
        "${session.durationMinutes} min",
        session.intensity?.let(::intensityLabel),
        kindLabel(session.kind),
        sourceLabel(session.source),
    )

/** "Ride 90 min": a session in the week's list, without its intensity, kind, or source. */
fun sessionShort(session: TrainingSession): String =
    "${sportLabel(session.sport)} ${session.durationMinutes} min"

/** "Remove Ride, 90 min": the name of a session's remove button. */
fun removeSessionLabel(session: TrainingSession): String =
    "Remove ${sportLabel(session.sport)}, ${session.durationMinutes} min"

/**
 * A day's planned calories against its target: the figure, then "of 3120 kcal" while targets are
 * on, or "kcal" while they are off. No meals is nothing yet; meals of unknown calories a dash,
 * never a zero. [said] is the figure in words, for a screen reader.
 */
class DayFuelLine(val value: String, val detail: String?, val said: String)

fun dayFuelLine(target: FuelTarget?, planned: PlannedDayTotal?): DayFuelLine =
    dayFuelLine(target?.calories?.toDouble(), planned?.meals ?: 0, planned?.total?.calories)

fun dayFuelLine(targetCalories: Double?, meals: Int, plannedCalories: Double?): DayFuelLine {
    val calories = if (meals > 0) plannedCalories else null
    val value = calories?.let { jsRound(it).toString() } ?: "–"
    val said =
        when {
            calories != null -> value
            meals > 0 -> "calories unknown"
            else -> "nothing yet"
        }
    if (targetCalories != null) {
        return DayFuelLine(value, "of ${jsRound(targetCalories)} kcal", said)
    }
    return DayFuelLine(value, if (calories == null) null else "kcal", said)
}

/** JavaScript's `Math.round`: halves go up, so -0.5 is 0. */
private fun jsRound(value: Double): Long = kotlin.math.floor(value + 0.5).roundToLong()

/** "Connected as i1. Last synced 12 Oct 2026, 21:14.": one line on where the connection stands. */
fun connectionSummary(connection: IntervalsIcuConnection, time: (Instant) -> String): String =
    when (connection.status) {
        IntervalsIcuConnectionStatus.CONNECTED ->
            connection.lastSyncAt?.let {
                "Connected as ${connection.athleteId}. Last synced ${time(it)}."
            } ?: "Connected as ${connection.athleteId}. Not synced yet."
        IntervalsIcuConnectionStatus.BROKEN ->
            connection.lastError
                ?: "intervals.icu no longer accepts the saved API key. Enter it again."
        else -> "Not connected."
    }

/** Why the last sync did not work, or when the next may run, for a connection that still works. */
fun syncNote(connection: IntervalsIcuConnection, time: (Instant) -> String): String? {
    if (connection.status != IntervalsIcuConnectionStatus.CONNECTED) return null
    connection.retryAt?.let {
        return "intervals.icu asked Mimos to wait. Training syncs again after ${time(it)}."
    }
    return connection.lastError
}

private val shortMonths =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "12 Oct 2026, 21:14" in [zone]: when a sync ran, as the web's medium date and short time. */
fun syncTime(instant: Instant, zone: TimeZone): String {
    val local = instant.toLocalDateTime(zone)
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "${local.day} ${shortMonths[local.month.ordinal]} ${local.year}, $hour:$minute"
}

enum class HintField {
    WeightKg,
    Sex,
    FtpWatts,
}

/** A value from intervals.icu the person may copy into a field of their fuel profile. */
data class Suggestion(val field: HintField, val value: String, val text: String, val action: String)

private fun sexName(sex: FuelSex): String =
    when (sex) {
        FuelSex.FEMALE -> "female"
        FuelSex.MALE -> "male"
        else -> "not given"
    }

/** JavaScript's `Number` of a field: blank is 0, anything not a number is NaN. */
private fun fieldNumber(text: String): Double {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0.0
    return if (DECIMAL.matches(trimmed)) trimmed.toDouble() else Double.NaN
}

private val DECIMAL = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

/**
 * intervals.icu's weight, sex, and FTP where they differ from the form: suggestions only, never
 * saved until the person presses Save (ADR-0022).
 */
fun hintSuggestions(draft: FuelDraft, hints: IntervalsIcuHints?): List<Suggestion> {
    if (hints == null) return emptyList()
    val suggestions = mutableListOf<Suggestion>()
    hints.weightKg
        ?.takeIf { fieldNumber(draft.weightKg) != it }
        ?.let {
            val kg = numberText(it)
            suggestions +=
                Suggestion(
                    HintField.WeightKg,
                    kg,
                    "intervals.icu has $kg kg.",
                    "Use $kg kg from intervals.icu",
                )
        }
    hints.sex
        ?.takeIf { it != draft.sex && it != FuelSex.unknown_default_open_api }
        ?.let {
            suggestions +=
                Suggestion(
                    HintField.Sex,
                    it.value,
                    "intervals.icu has ${sexName(it)}.",
                    "Use ${sexName(it)} from intervals.icu",
                )
        }
    hints.ftpWatts
        ?.takeIf { fieldNumber(draft.ftpWatts) != it.toDouble() }
        ?.let {
            suggestions +=
                Suggestion(
                    HintField.FtpWatts,
                    it.toString(),
                    "intervals.icu has $it W.",
                    "Use $it W from intervals.icu",
                )
        }
    return suggestions
}

/** While the profile's body fat is empty, intervals.icu's already counts; this says so. */
fun bodyFatNote(field: String, hints: IntervalsIcuHints?): String? {
    val synced = hints?.bodyFatPercent
    if (field.isNotBlank() || synced == null) return null
    return "Optional. While this is empty, your targets use ${numberText(synced)}% from intervals.icu."
}

/** The Fuel screen's form, as typed. */
data class FuelDraft(
    val enabled: Boolean = false,
    val weightKg: String = "",
    val sex: FuelSex = FuelSex.UNSPECIFIED,
    val bodyFatPercent: String = "",
    val ftpWatts: String = "",
    val goal: FuelGoal = FuelGoal.FUEL,
    val proteinGPerKg: String = "",
) {
    /** Takes a suggestion into its field. */
    fun with(suggestion: Suggestion): FuelDraft =
        when (suggestion.field) {
            HintField.WeightKg -> copy(weightKg = suggestion.value)
            HintField.Sex ->
                copy(sex = FuelSex.entries.firstOrNull { it.value == suggestion.value } ?: sex)
            HintField.FtpWatts -> copy(ftpWatts = suggestion.value)
        }

    /** A goal sets the protein to its own, which the person may then change. */
    fun withGoal(goal: Goal): FuelDraft =
        copy(goal = goal.value, proteinGPerKg = numberText(goal.proteinGPerKg))
}

fun FuelProfile.toDraft(): FuelDraft =
    FuelDraft(
        enabled = enabled,
        weightKg = numberText(weightKg),
        sex = sex,
        bodyFatPercent = numberText(bodyFatPercent),
        ftpWatts = ftpWatts?.toString() ?: "",
        goal = goal,
        proteinGPerKg = numberText(proteinGPerKg),
    )

sealed interface CheckedProfile {
    data class Valid(val input: FuelProfileInput) : CheckedProfile

    data class Invalid(val fields: List<FuelField>) : CheckedProfile
}

/**
 * The profile to save, or the fields to fix first: a weight is needed, the FTP is whole watts, and
 * a value that is not a number is wrong where it is. Ranges are the API's to check, and its 400
 * names the fields.
 */
fun FuelDraft.check(): CheckedProfile {
    val wrong = mutableListOf<FuelField>()
    fun read(text: String, field: FuelField): Double? {
        if (text.isBlank()) return null
        val value = fieldNumber(text)
        if (value.isNaN()) wrong += field
        return value.takeUnless { it.isNaN() }
    }
    val weight = read(weightKg, FuelField.WeightKg)
    if (weight == null && FuelField.WeightKg !in wrong) wrong += FuelField.WeightKg
    val bodyFat = read(bodyFatPercent, FuelField.BodyFatPercent)
    val ftp = read(ftpWatts, FuelField.FtpWatts)
    if (ftp != null && ftp % 1.0 != 0.0) wrong += FuelField.FtpWatts
    val protein = read(proteinGPerKg, FuelField.ProteinGPerKg)
    if (wrong.isNotEmpty() || weight == null) {
        return CheckedProfile.Invalid(FuelField.entries.filter { it in wrong })
    }
    return CheckedProfile.Valid(
        FuelProfileInput(
            enabled = enabled,
            weightKg = weight,
            sex = sex,
            goal = goal,
            bodyFatPercent = bodyFat,
            ftpWatts = ftp?.toInt(),
            proteinGPerKg = protein,
        )
    )
}

/** A session being added by hand to a day, as typed. */
data class TrainingDraft(
    val sport: TrainingSport = TrainingSport.RIDE,
    val minutes: String = "",
    val intensity: TrainingIntensity = TrainingIntensity.ENDURANCE,
    val minutesError: Boolean = false,
)

/** The session to add on [date], or null while the minutes are not a whole number. */
fun TrainingDraft.input(date: LocalDate): TrainingSessionInput? {
    val minutes = minutes.trim().takeIf { WHOLE.matches(it) }?.toIntOrNull() ?: return null
    return TrainingSessionInput(date, sport, minutes, intensity)
}

private val WHOLE = Regex("""[+-]?\d+""")
