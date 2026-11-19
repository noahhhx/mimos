package io.github.noahhhx.mimos.fuel

import io.github.noahhhx.mimos.api.models.FuelGoal
import io.github.noahhhx.mimos.api.models.FuelSex
import io.github.noahhhx.mimos.api.models.IntervalsIcuConnection
import io.github.noahhhx.mimos.api.models.IntervalsIcuHints
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.PlannedDayTotal
import io.github.noahhhx.mimos.api.models.TrainingIntensity
import io.github.noahhhx.mimos.api.models.TrainingKind
import io.github.noahhhx.mimos.api.models.TrainingSession
import io.github.noahhhx.mimos.api.models.TrainingSessionInput
import io.github.noahhhx.mimos.api.models.TrainingSource
import io.github.noahhhx.mimos.api.models.TrainingSport
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

@Serializable
private class GoalCase(val value: FuelGoal, val label: String, val proteinGPerKg: Double)

@Serializable private class SexCase(val value: FuelSex, val label: String)

@Serializable
private class NamedFieldsCase(
    val about: String,
    val detail: String? = null,
    val profile: List<String>,
    val training: List<String>,
)

@Serializable
private class SessionSketch(
    val source: TrainingSource,
    val kind: TrainingKind,
    val sport: TrainingSport,
    val durationMinutes: Int,
    val intensity: TrainingIntensity? = null,
    val workKj: Double? = null,
)

@Serializable
private class SessionCase(
    val about: String,
    val session: SessionSketch,
    val parts: List<String>,
    val short: String,
    val remove: String,
)

@Serializable
private class ConnectionCase(
    val about: String,
    val connection: IntervalsIcuConnection,
    val summary: String,
    val note: String?,
)

@Serializable private class HintForm(val weightKg: String, val sex: FuelSex, val ftpWatts: String)

@Serializable
private class ExpectedSuggestion(
    val field: String,
    val value: String,
    val text: String,
    val action: String,
)

@Serializable
private class HintCase(
    val about: String,
    val form: HintForm,
    val hints: IntervalsIcuHints? = null,
    val suggestions: List<ExpectedSuggestion>,
)

@Serializable
private class BodyFatCase(
    val about: String,
    val field: String,
    val hints: IntervalsIcuHints? = null,
    val note: String?,
)

@Serializable private class TargetSketch(val calories: Double)

@Serializable private class PlannedSketch(val meals: Int, val total: Nutrition)

@Serializable private class ExpectedLine(val value: String, val detail: String?, val said: String)

@Serializable
private class LedgerCase(
    val about: String,
    val target: TargetSketch? = null,
    val planned: PlannedSketch? = null,
    val expected: ExpectedLine,
)

@Serializable
private class FuelCases(
    val goals: List<GoalCase>,
    val sexes: List<SexCase>,
    val fieldErrors: Map<String, String>,
    val trainingFieldErrors: Map<String, String>,
    val trainingFailures: Map<String, String>,
    val intervalsIcuFailures: Map<String, String>,
    val sports: Map<String, String>,
    val intensities: Map<String, String>,
    val namedFieldsCases: List<NamedFieldsCase>,
    val sessionCases: List<SessionCase>,
    val connectionCases: List<ConnectionCase>,
    val hintCases: List<HintCase>,
    val bodyFatCases: List<BodyFatCase>,
    val ledgerFuelCases: List<LedgerCase>,
)

private val cases = MimosJson.decodeFromString<FuelCases>(TestCases.read("fuel.json"))

private fun session(sketch: SessionSketch) =
    TrainingSession(
        "7f1c2d9e-5b3a-4c8e-9d21-6a0b4e3f8c17",
        LocalDate(2026, 10, 13),
        sketch.source,
        sketch.kind,
        sketch.sport,
        sketch.durationMinutes,
        sketch.workKj,
        sketch.intensity,
    )

private val hintFieldNames =
    mapOf(
        HintField.WeightKg to "weightKg",
        HintField.Sex to "sex",
        HintField.FtpWatts to "ftpWatts",
    )

class FuelRulesTest {
    @Test
    fun `the form offers the web's goals, sexes, and errors`() {
        assertEquals(
            cases.goals.map { Triple(it.value, it.label, it.proteinGPerKg) },
            GOALS.map { Triple(it.value, it.label, it.proteinGPerKg) },
        )
        assertEquals(cases.sexes.map { it.value to it.label }, SEXES)
        assertEquals(cases.fieldErrors, FuelField.entries.associate { it.apiName to it.error })
        assertEquals(cases.trainingFieldErrors, mapOf(DURATION_FIELD to DURATION_ERROR))
        assertEquals(
            cases.trainingFailures,
            mapOf("add" to TrainingFailures.ADD, "remove" to TrainingFailures.REMOVE),
        )
        assertEquals(
            cases.intervalsIcuFailures,
            mapOf(
                "load" to IntervalsIcuFailures.LOAD,
                "connect" to IntervalsIcuFailures.CONNECT,
                "sync" to IntervalsIcuFailures.SYNC,
                "disconnect" to IntervalsIcuFailures.DISCONNECT,
            ),
        )
        assertEquals(cases.sports, SPORTS.associate { it.first.value to it.second })
        assertEquals(cases.intensities, INTENSITIES.associate { it.first.value to it.second })
    }

    @Test
    fun `a 400's detail names the fields the web reads from it`() {
        for (case in cases.namedFieldsCases) {
            assertEquals(
                case.profile,
                namedProfileFields(case.detail).map { it.apiName },
                case.about,
            )
            assertEquals(
                case.training,
                namedFields(case.detail, listOf(DURATION_FIELD)),
                case.about,
            )
        }
    }

    @Test
    fun `a session reads as on the web`() {
        for (case in cases.sessionCases) {
            val session = session(case.session)
            assertEquals(case.parts, sessionParts(session), case.about)
            assertEquals(case.short, sessionShort(session), case.about)
            assertEquals(case.remove, removeSessionLabel(session), case.about)
        }
    }

    @Test
    fun `the connection's summary and note read as on the web`() {
        val time = { instant: Instant -> "<$instant>" }
        for (case in cases.connectionCases) {
            assertEquals(case.summary, connectionSummary(case.connection, time), case.about)
            assertEquals(case.note, syncNote(case.connection, time), case.about)
        }
    }

    @Test
    fun `intervals icu's values are suggested where they differ from the form`() {
        for (case in cases.hintCases) {
            val draft =
                FuelDraft(
                    weightKg = case.form.weightKg,
                    sex = case.form.sex,
                    ftpWatts = case.form.ftpWatts,
                )
            assertEquals(
                case.suggestions.map { listOf(it.field, it.value, it.text, it.action) },
                hintSuggestions(draft, case.hints).map {
                    listOf(hintFieldNames.getValue(it.field), it.value, it.text, it.action)
                },
                case.about,
            )
        }
    }

    @Test
    fun `synced body fat is noted while the field is empty`() {
        for (case in cases.bodyFatCases) {
            assertEquals(case.note, bodyFatNote(case.field, case.hints), case.about)
        }
    }

    @Test
    fun `a day's planned calories read against its target as on the web`() {
        for (case in cases.ledgerFuelCases) {
            val line =
                dayFuelLine(
                    case.target?.calories,
                    case.planned?.meals ?: 0,
                    case.planned?.total?.calories,
                )
            assertEquals(
                listOf(case.expected.value, case.expected.detail, case.expected.said),
                listOf(line.value, line.detail, line.said),
                case.about,
            )
        }
    }

    @Test
    fun `a suggestion goes into its field, and a goal sets its protein`() {
        val draft = FuelDraft(weightKg = "72")
        val hints = IntervalsIcuHints(weightKg = 70.8, sex = FuelSex.MALE, ftpWatts = 282)
        val taken = hintSuggestions(draft, hints).fold(draft) { form, it -> form.with(it) }
        assertEquals(FuelDraft(weightKg = "70.8", sex = FuelSex.MALE, ftpWatts = "282"), taken)
        assertEquals(emptyList(), hintSuggestions(taken, hints))
        assertEquals("2.2", draft.withGoal(GOALS[2]).proteinGPerKg)
        assertEquals(FuelGoal.FASTER_LOSS, draft.withGoal(GOALS[2]).goal)
    }

    @Test
    fun `the profile is checked before it is sent`() {
        val valid =
            FuelDraft(
                    enabled = true,
                    weightKg = " 70.5 ",
                    sex = FuelSex.FEMALE,
                    ftpWatts = "250",
                    proteinGPerKg = "1.6",
                )
                .check()
        valid as CheckedProfile.Valid
        assertEquals(70.5, valid.input.weightKg)
        assertEquals(250, valid.input.ftpWatts)
        assertNull(valid.input.bodyFatPercent, "an empty field is left out")
        assertEquals(1.6, valid.input.proteinGPerKg)

        assertEquals(
            CheckedProfile.Invalid(listOf(FuelField.WeightKg)),
            FuelDraft(weightKg = "").check(),
        )
        assertEquals(
            CheckedProfile.Invalid(listOf(FuelField.WeightKg, FuelField.FtpWatts)),
            FuelDraft(weightKg = "seventy", ftpWatts = "250.5").check(),
        )
        assertEquals(
            CheckedProfile.Invalid(listOf(FuelField.BodyFatPercent)),
            FuelDraft(weightKg = "70", bodyFatPercent = "2f").check(),
        )
    }

    @Test
    fun `a session is added only with whole minutes`() {
        val day = LocalDate(2026, 11, 19)
        assertEquals(
            TrainingSessionInput(day, TrainingSport.RUN, 45, TrainingIntensity.HARD),
            TrainingDraft(TrainingSport.RUN, " 45 ", TrainingIntensity.HARD).input(day),
        )
        assertNull(TrainingDraft(minutes = "").input(day))
        assertNull(TrainingDraft(minutes = "45.5").input(day))
        assertNull(TrainingDraft(minutes = "lots").input(day))
    }

    @Test
    fun `a sync's time reads in the phone's zone`() {
        val instant = Instant.parse("2026-10-12T21:04:00Z")
        assertEquals("12 Oct 2026, 21:04", syncTime(instant, TimeZone.UTC))
        assertEquals("13 Oct 2026, 06:04", syncTime(instant, TimeZone.of("Asia/Tokyo")))
    }

    @Test
    fun `a planned day with a target reads through its totals`() {
        val planned = PlannedDayTotal(LocalDate(2026, 11, 19), Nutrition(calories = 1840.0), 2, 0)
        val line = dayFuelLine(null, planned)
        assertEquals("1840", line.value)
        assertEquals("kcal", line.detail)
    }
}
