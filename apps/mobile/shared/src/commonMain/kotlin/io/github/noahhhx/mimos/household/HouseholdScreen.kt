package io.github.noahhhx.mimos.household

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.noahhhx.mimos.api.models.HouseholdInvitePreview
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.ListRow
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField

/**
 * The Household screen: the household, or an invite to join. [onShare] hands a link to the phone's
 * share sheet; [onSwitchInstance] signs out and connects to the instance an invite is for.
 */
@Composable
fun HouseholdScreen(
    model: HouseholdModel,
    onShare: (String) -> Unit,
    onSwitchInstance: (InviteLink) -> Unit,
) {
    LaunchedEffect(Unit) { model.ensureLoaded() }
    when (val route = model.route.collectAsState().value) {
        HouseholdRoute.Page -> HouseholdPage(model, onShare)
        is HouseholdRoute.Join -> JoinPage(model, onSwitchInstance)
    }
}

@Composable
private fun Page(
    back: String? = null,
    onBack: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xl),
    ) {
        if (back != null) {
            Text(
                back,
                gutter
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(vertical = MimosTheme.spacing.s),
                color = MimosTheme.colors.muted,
            )
        }
        content()
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    Column(
        gutter.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
    ) {
        Text(title, style = MimosTheme.typography.heading)
        content()
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
}

@Composable
private fun Consequences(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
        for (line in lines) {
            Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
                Text("•")
                Text(line)
            }
        }
    }
}

@Composable
private fun HouseholdPage(model: HouseholdModel, onShare: (String) -> Unit) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val household by model.household.collectAsState()
    Page {
        PageHeader(title = "Household", eyebrow = "Account", modifier = gutter)
        Section("Sharing a kitchen") {
            Text(
                "Everyone in a household shares its recipes, ingredients, meal plans, shopping " +
                    "lists and plugins. Each person's food log stays their own."
            )
        }
        Text("Who is in it", gutter, style = MimosTheme.typography.heading)
        when (val state = household) {
            HouseholdState.Loading -> Muted("Loading your household…", gutter)
            HouseholdState.Failed ->
                ErrorState("Could not load your household.", model::retry, gutter)
            is HouseholdState.Loaded -> {
                Column {
                    for (member in state.household.members) {
                        ListRow(member.displayName, trailing = if (member.you) "You" else null)
                    }
                }
                if (!state.shared) Muted("It is just you for now.", gutter)
            }
        }
        InviteSection(model, onShare)
        PasteSection(model)
        if ((household as? HouseholdState.Loaded)?.shared == true) LeaveSection(model)
    }
}

@Composable
private fun InviteSection(model: HouseholdModel, onShare: (String) -> Unit) {
    val invite by model.invite.collectAsState()
    Section("Invite someone") {
        Text(
            "Create a link and send it to the person you want to share with. It works once, for " +
                "seven days, and they choose whether to join."
        )
        Button(
            when (invite) {
                InviteState.Creating -> "Creating…"
                is InviteState.Created -> "Create another link"
                else -> "Create invite link"
            },
            onClick = model::createInvite,
            enabled = invite != InviteState.Creating,
        )
        when (val state = invite) {
            InviteState.Failed -> Notice("Could not create an invite.")
            is InviteState.Created -> {
                SelectionContainer { Text(state.link) }
                Button(
                    "Share link",
                    onClick = { onShare(state.link) },
                    kind = ButtonKind.Secondary,
                )
                Muted("Works once, until ${dateTimeText(state.invite.expiresAt)}.")
            }
            else -> {}
        }
    }
}

@Composable
private fun PasteSection(model: HouseholdModel) {
    val paste by model.paste.collectAsState()
    Section("Join a household") {
        Text("Someone sent you an invite link? Paste it here to see their household.")
        TextField(
            value = paste.text,
            onValueChange = model::editPaste,
            label = "Invite link",
            error = paste.error,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { model.openPasted() }),
        )
        Button(
            "Open invite",
            onClick = model::openPasted,
            kind = ButtonKind.Secondary,
            enabled = paste.text.isNotBlank(),
        )
    }
}

@Composable
private fun LeaveSection(model: HouseholdModel) {
    val leave by model.leave.collectAsState()
    Section("Leave the household") {
        when (val state = leave) {
            LeaveState.Idle ->
                Button("Leave household", model::startLeaving, kind = ButtonKind.Secondary)
            is LeaveState.Confirming -> {
                Text("Leave this household?")
                Consequences(
                    listOf(
                        "The household keeps its recipes, ingredients, meal plans and shopping " +
                            "lists, including the ones you added. Export your data first if you " +
                            "want a copy.",
                        "You stop eating the meals planned there, and a meal only you eat is " +
                            "removed.",
                        "You keep your food log, but your logged meals no longer link to the " +
                            "household's recipes.",
                        "You keep your fuel settings and training.",
                        "You start again in a household of your own.",
                    )
                )
                state.error?.let { Notice(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
                    Button(
                        if (state.leaving) "Leaving…" else "Leave household",
                        model::confirmLeave,
                        kind = ButtonKind.Danger,
                        enabled = !state.leaving,
                    )
                    Button(
                        "Stay",
                        model::stay,
                        kind = ButtonKind.Secondary,
                        enabled = !state.leaving,
                    )
                }
            }
        }
    }
}

@Composable
private fun JoinPage(model: HouseholdModel, onSwitchInstance: (InviteLink) -> Unit) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val join by model.join.collectAsState()
    Page("← Household", { model.back() }) {
        when (val state = join) {
            JoinState.Loading -> {
                PageHeader(title = "Invite", eyebrow = "Household", modifier = gutter)
                Muted("Loading the invite…", gutter)
            }
            JoinState.Failed -> {
                PageHeader(title = "Invite", eyebrow = "Household", modifier = gutter)
                ErrorState("Could not load the invite.", model::retryJoin, gutter)
            }
            is JoinState.Invalid -> {
                PageHeader(
                    title = "This invite cannot be used",
                    eyebrow = "Household",
                    modifier = gutter,
                )
                Notice(state.message, gutter)
                Button(
                    "Back to your household",
                    { model.back() },
                    gutter,
                    kind = ButtonKind.Secondary,
                )
            }
            JoinState.AlreadyMember -> {
                PageHeader(
                    title = "You are already in this household",
                    eyebrow = "Household",
                    modifier = gutter,
                )
                Button("See your household", { model.back() }, gutter, kind = ButtonKind.Secondary)
            }
            is JoinState.OtherInstance -> OtherInstance(state, model, onSwitchInstance)
            is JoinState.Ready -> Ready(state, model)
        }
    }
}

@Composable
private fun OtherInstance(
    state: JoinState.OtherInstance,
    model: HouseholdModel,
    onSwitchInstance: (InviteLink) -> Unit,
) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val theirs = state.link.instance.origin
    PageHeader(title = "This invite is for another Mimos", eyebrow = "Household", modifier = gutter)
    Section("Where it was made") {
        Text("The invite is for $theirs, and the app is connected to ${state.connected.origin}.")
        Text(
            "Switch to $theirs to sign in there and see the invite. Switching signs you out " +
                "here, and the app keeps one Mimos at a time."
        )
    }
    Row(gutter, horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
        Button("Switch", { onSwitchInstance(state.link) })
        Button("Not now", { model.back() }, kind = ButtonKind.Secondary)
    }
}

@Composable
private fun Ready(state: JoinState.Ready, model: HouseholdModel) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val preview = state.preview
    val names = preview.members.map { it.displayName }
    PageHeader(title = "Join ${householdName(names)}?", eyebrow = "Household", modifier = gutter)
    Section("What happens when you join") {
        Text(
            "You will share recipes, ingredients, meal plans, shopping lists and plugins with " +
                "${listNames(names)}."
        )
        Consequences(consequencesOfJoining(preview))
        Muted("This invite works until ${dateTimeText(preview.expiresAt)}.")
    }
    state.error?.let { Notice(it, gutter) }
    Row(gutter, horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
        Button(
            if (state.joining) "Joining…" else "Join household",
            model::confirmJoin,
            enabled = !state.joining,
        )
        Button("Not now", { model.back() }, kind = ButtonKind.Secondary, enabled = !state.joining)
    }
}

private fun consequencesOfJoining(preview: HouseholdInvitePreview): List<String> =
    if (preview.currentHouseholdShared) {
        listOf(
            "You leave the household you are in now. Its recipes, ingredients, meal plans and " +
                "shopping lists stay with it, including the ones you added.",
            "You stop eating the meals planned there, and your logged meals no longer link to " +
                "its recipes.",
            "Your food log, fuel settings and training stay yours. Nobody else sees them.",
        )
    } else {
        listOf(
            "Your recipes and your own ingredients come with you, and everyone in the " +
                "household can change them.",
            "Your meal plans, shopping lists and plugin settings are deleted. You use the " +
                "household's instead.",
            "Your food log, fuel settings and training stay yours. Nobody else sees them.",
        )
    }
