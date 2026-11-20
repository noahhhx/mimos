package io.github.noahhhx.mimos.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.noahhhx.mimos.api.models.PanelHighlightBlock
import io.github.noahhhx.mimos.api.models.PlanSuggestion
import io.github.noahhhx.mimos.client.PanelBlock
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.NoticeKind
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.Text

/*
 * What the plugins a household turned on put on its plan (ADR-0006, ADR-0017): each plugin's
 * panel for the week, collapsed to one line until opened, and the suggestion cards under them.
 * Every string is a plugin's, shown as plain text and attributed to it.
 */

/** The week's panels, one under another between rules; nothing when there are none. */
@Composable
fun WeekPanelsGroup(model: WeekPanelsModel, week: WeekPanels, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Label("Plugins for this week", Modifier.padding(bottom = MimosTheme.spacing.s))
        Rule()
        for (state in week.panels) {
            key(week.monday, state.panel.pluginId) { WeekPanelSection(model, state) }
        }
    }
}

@Composable
private fun WeekPanelSection(model: WeekPanelsModel, state: PanelState) {
    val colors = MimosTheme.colors
    val panel = state.panel
    val summary = panel.summary
    val name = listOfNotNull(panel.pluginName, summary?.label).joinToString(" ")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { model.toggle(panel.pluginId) }
                .semantics {
                    contentDescription = name
                    stateDescription = if (state.open) "Expanded" else "Collapsed"
                }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                panel.pluginName.uppercase(),
                style = MimosTheme.typography.label,
                color = if (state.open) colors.fg else colors.muted,
            )
            Text(
                listOfNotNull(summary?.icon, summary?.label).joinToString(" "),
                Modifier.weight(1f),
                style = MimosTheme.typography.person,
            )
            Text(
                if (state.open) "−" else "+",
                style = MimosTheme.typography.person,
                color = colors.muted,
            )
        }
        if (state.open) {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
            ) {
                for ((blockKey, block) in shownBlocks(panel.blocks, state.spinning)) {
                    key(blockKey) { PanelBlockView(model, state, block) }
                }
                state.error?.let { Notice(it) }
            }
        }
        Rule()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelBlockView(model: WeekPanelsModel, state: PanelState, block: PanelBlock) {
    val pluginId = state.panel.pluginId
    when (block) {
        is PanelBlock.Text -> Text(block.block.text)
        is PanelBlock.Highlight -> Highlight(block.block)
        is PanelBlock.Wheel ->
            Wheel(
                block.block.segments,
                block.block.landing,
                spin = state.spinning,
                onStop = { model.spinStopped(pluginId) },
            )
        is PanelBlock.Actions ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
                verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
            ) {
                for (button in block.block.actions) {
                    Button(
                        button.label,
                        { model.press(pluginId, button) },
                        kind =
                            if (button.primary == true) ButtonKind.Primary
                            else ButtonKind.Secondary,
                        enabled = state.buttonsEnabled,
                    )
                }
            }
        // A block type this app does not know yet (ADR-0017): skipped.
        is PanelBlock.Unknown -> {}
    }
}

@Composable
private fun Highlight(block: PanelHighlightBlock) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        block.icon?.let { Text(it, style = TextStyle(fontSize = 48.sp, lineHeight = 1.em)) }
        Column {
            Text(
                block.title,
                style = MimosTheme.typography.lede.copy(fontSize = 30.sp, lineHeight = 1.2.em),
            )
            block.text?.let { Text(it, color = MimosTheme.colors.muted) }
        }
    }
}

/**
 * The plugins' suggestion cards for the week, each with the meals it would add and the plugin it
 * comes from, and what the last one applied did.
 */
@Composable
fun SuggestionsSection(model: PlanModel, week: PlanWeek, modifier: Modifier = Modifier) {
    val notice by model.notice.collectAsState()
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
    ) {
        Column {
            Text("Suggestions", style = MimosTheme.typography.heading)
            Text(
                "From the plugins you turned on.",
                style = MimosTheme.typography.small,
                color = MimosTheme.colors.muted,
            )
        }
        notice?.let { Notice(it, kind = NoticeKind.Ok) }
        for (suggestion in week.suggestions) {
            key(suggestion.pluginId, suggestion.title) { SuggestionCard(model, suggestion) }
        }
    }
}

@Composable
private fun SuggestionCard(model: PlanModel, suggestion: PlanSuggestion) {
    val colors = MimosTheme.colors
    val busy by model.busy.collectAsState()
    val applying by model.applying.collectAsState()
    Column(
        Modifier.fillMaxWidth()
            .background(colors.bg)
            .border(MimosTheme.spacing.hairline, colors.line)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            suggestion.icon?.let {
                Text(
                    it,
                    Modifier.border(MimosTheme.spacing.hairline, colors.line)
                        .padding(horizontal = 6.dp),
                    style = MimosTheme.typography.label,
                    color = colors.accent,
                )
            }
            Text(suggestion.title, style = MimosTheme.typography.heading)
        }
        suggestion.blurb?.let { Text(it) }
        Column {
            Rule()
            for (entry in suggestion.propertyEntries) {
                Text(
                    "${entry.recipeTitle} · ${dayLabel(entry.date)} ${mealLabel(entry.mealType)} · " +
                        formatServings(entry.servings),
                    Modifier.padding(vertical = 6.dp),
                    style = MimosTheme.typography.body.copy(fontSize = 15.sp),
                )
                Rule()
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                if (applying) "Adding…" else "Add to plan",
                { model.applySuggestion(suggestion) },
                enabled = !busy,
            )
            Text(
                "via ${suggestion.pluginName}",
                style = MimosTheme.typography.small,
                color = colors.muted,
            )
        }
    }
}
