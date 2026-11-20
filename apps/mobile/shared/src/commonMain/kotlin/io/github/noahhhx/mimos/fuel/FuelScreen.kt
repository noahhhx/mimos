package io.github.noahhhx.mimos.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.api.models.IntervalsIcuConnection
import io.github.noahhhx.mimos.api.models.IntervalsIcuConnectionStatus
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.Choice
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField
import io.github.noahhhx.mimos.ui.TickBox
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * The Fuel screen, as the web's Fuel page: what Fuel for the day is, the profile that turns it on,
 * and the intervals.icu connection when the instance offers it.
 */
@Composable
fun FuelScreen(model: FuelModel) {
    LaunchedEffect(Unit) { model.load() }
    val profile by model.profile.collectAsState()
    val connection by model.connection.collectAsState()
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MimosTheme.spacing.page, vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xl),
    ) {
        PageHeader(title = "Fuel", eyebrow = "Account")
        when (val state = profile) {
            ProfileState.Loading ->
                Text("Loading your fuel settings…", color = MimosTheme.colors.muted)
            ProfileState.Failed -> ErrorState("Could not load your fuel settings.", model::load)
            is ProfileState.Loaded -> {
                Section(FUEL_LABEL) {
                    Text(
                        "Turn this on to see a calorie, carb, protein, and fat target for each " +
                            "day beside what you planned and logged. The targets rise with your " +
                            "training and come from your weight, your goal, and the training you " +
                            "add or bring in from intervals.icu."
                    )
                    Text(
                        "They are yours alone: everyone in a household has their own settings, " +
                            "and plugins never see them."
                    )
                }
                Rule()
                ProfileSection(model, state.form, (connection as? ConnectionState.Loaded))
            }
        }
        when (val state = connection) {
            is ConnectionState.Loaded -> {
                Rule()
                IntervalsIcuSection(model, state.connection)
            }
            ConnectionState.Failed -> Notice(IntervalsIcuFailures.LOAD)
            else -> {}
        }
    }
}

@Composable
private fun ProfileSection(
    model: FuelModel,
    form: ProfileForm,
    connection: ConnectionState.Loaded?,
) {
    val draft = form.draft
    val hints = connection?.connection?.hints
    val suggestions = hintSuggestions(draft, hints)
    fun suggestionFor(field: HintField) = suggestions.find { it.field == field }
    fun errorFor(field: FuelField) = field.error.takeIf { field in form.fieldErrors }
    Column(verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.l)) {
        form.error?.let { Notice(it) }
        CheckRow(
            "Show fuel targets",
            draft.enabled,
            onToggle = { model.edit { it.copy(enabled = !it.enabled) } },
        )
        Text("About you", style = MimosTheme.typography.heading)
        NumberField(
            "Weight (kg)",
            draft.weightKg,
            { text -> model.edit { it.copy(weightKg = text) } },
            errorFor(FuelField.WeightKg),
        )
        suggestionFor(HintField.WeightKg)?.let { SuggestionLine(it, model::use) }
        Column {
            for ((sex, label) in SEXES) {
                Choice(label, draft.sex == sex, { model.edit { it.copy(sex = sex) } })
            }
        }
        suggestionFor(HintField.Sex)?.let { SuggestionLine(it, model::use) }
        NumberField(
            "Body fat (%)",
            draft.bodyFatPercent,
            { text -> model.edit { it.copy(bodyFatPercent = text) } },
            errorFor(FuelField.BodyFatPercent),
            hint =
                bodyFatNote(draft.bodyFatPercent, hints)
                    ?: "Optional. Without it, Mimos assumes 15% for men, 25% for women, and " +
                        "20% otherwise.",
        )
        NumberField(
            "FTP (watts)",
            draft.ftpWatts,
            { text -> model.edit { it.copy(ftpWatts = text) } },
            errorFor(FuelField.FtpWatts),
            hint = "Optional. Your functional threshold power on the bike.",
            whole = true,
        )
        suggestionFor(HintField.FtpWatts)?.let { SuggestionLine(it, model::use) }
        Text("Your goal", style = MimosTheme.typography.heading)
        Column {
            for (goal in GOALS) {
                Choice(goal.label, draft.goal == goal.value, { model.chooseGoal(goal) })
            }
        }
        NumberField(
            "Protein (g per kg)",
            draft.proteinGPerKg,
            { text -> model.edit { it.copy(proteinGPerKg = text) } },
            errorFor(FuelField.ProteinGPerKg),
            hint = "Set by your goal. Change it if you like.",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(if (form.saving) "Saving…" else "Save", model::save, enabled = !form.saving)
            if (form.saved) {
                Text(
                    "Saved.",
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MimosTheme.colors.muted,
                )
            }
        }
    }
}

/** A number field; its hint shows below it until there is an error to show instead. */
@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    hint: String? = null,
    whole: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs)) {
        TextField(
            value,
            onValueChange,
            label,
            error = error,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal
                ),
        )
        if (hint != null && error == null) {
            Text(hint, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionLine(suggestion: Suggestion, onUse: (Suggestion) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(suggestion.text, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
        Button(
            "Use it",
            { onUse(suggestion) },
            kind = ButtonKind.Secondary,
            small = true,
            description = suggestion.action,
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TickBox(checked)
        Text(label)
    }
}

private val syncedAt = { instant: Instant -> syncTime(instant, TimeZone.currentSystemDefault()) }

/**
 * Connect with an athlete id and API key, see when training last synced, sync now, or disconnect. A
 * broken connection asks for the key again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalsIcuSection(model: FuelModel, connection: IntervalsIcuConnection) {
    val link by model.link.collectAsState()
    val connected = connection.status == IntervalsIcuConnectionStatus.CONNECTED
    val broken = connection.status == IntervalsIcuConnectionStatus.BROKEN
    Section("intervals.icu") {
        Text(
            "Connect intervals.icu to bring in your planned workouts and completed activities, " +
                "so each day's fuel follows your training. Mimos checks for changes on its own, " +
                "and Refresh checks now."
        )
        val summary = connectionSummary(connection, syncedAt)
        if (broken) {
            Notice(summary)
        } else {
            Text(summary, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        syncNote(connection, syncedAt)?.let {
            Text(it, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
        }
        link.problem?.let { Notice(it) }
        if (!connected) {
            TextField(
                link.athleteId,
                model::editAthleteId,
                "Athlete ID",
                placeholder = "i12345",
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            TextField(link.apiKey, model::editApiKey, "API key", password = true)
            Text(
                "Both are in intervals.icu under Settings, in the Developer Settings section. " +
                    "Mimos keeps the key encrypted and never shows it again.",
                style = MimosTheme.typography.small,
                color = MimosTheme.colors.muted,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (connected) {
                Button(if (link.busy) "Syncing…" else "Refresh", model::sync, enabled = !link.busy)
            } else {
                Button(
                    if (link.busy) "Connecting…" else "Connect",
                    model::connect,
                    enabled = link.canConnect,
                )
            }
            if (connected || broken) DisconnectControls(model, link)
        }
    }
}

@Composable
private fun DisconnectControls(model: FuelModel, link: LinkForm) {
    if (!link.confirmingDisconnect) {
        Button(
            "Disconnect",
            model::startDisconnect,
            kind = ButtonKind.Secondary,
            enabled = !link.busy,
        )
        return
    }
    Text("Disconnect? Training from intervals.icu goes; training you added stays.")
    Button(
        "Yes, disconnect",
        model::disconnect,
        kind = ButtonKind.Secondary,
        enabled = !link.busy,
    )
    Button("Cancel", model::cancelDisconnect, kind = ButtonKind.Secondary, enabled = !link.busy)
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
    ) {
        Text(title, style = MimosTheme.typography.heading)
        content()
    }
}
