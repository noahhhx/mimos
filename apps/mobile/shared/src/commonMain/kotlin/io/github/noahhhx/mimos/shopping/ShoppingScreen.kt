package io.github.noahhhx.mimos.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.api.models.ShoppingListItem
import io.github.noahhhx.mimos.plan.dayLabel
import io.github.noahhhx.mimos.recipes.countedName
import io.github.noahhhx.mimos.recipes.form.unitLabel
import io.github.noahhhx.mimos.recipes.formatQuantity
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.EmptyState
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.KeepScreenOn
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.SelectField
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField
import io.github.noahhhx.mimos.ui.TickBox
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * The Shopping tab: the week's list by aisle, ticked with one thumb in the shop. A ticked item
 * leaves its aisle for the basket, folded at the end of the list; below it, a form for anything
 * else and the way to regenerate. The screen stays awake while it is open.
 */
@Composable
fun ShoppingScreen(
    model: ShoppingModel,
    onSendNow: () -> Unit = model::sendNow,
    /** False before the session is renewed: only ticking works then, so nothing else is offered. */
    signedIn: Boolean = true,
) {
    KeepScreenOn()
    LaunchedEffect(Unit) { model.ensureLoaded() }
    val spacing = MimosTheme.spacing
    val gutter = Modifier.padding(horizontal = spacing.page)
    val monday by model.monday.collectAsState()
    val state by model.state.collectAsState()
    val ticks by model.ticks.collectAsState()
    val generating by model.generating.collectAsState()
    val error by model.error.collectAsState()
    val online by model.online.collectAsState()
    var basketOpen by rememberSaveable { mutableStateOf(false) }
    val list = (state as? ListState.Loaded)?.list?.takeIf { it.startDate == monday }
    val view = list?.let {
        basketView(
            it.items.map { item ->
                ticks[item.id]?.let { tick -> item.copy(checked = tick.checked) } ?: item
            }
        )
    }
    @Composable
    fun Shown(item: ShoppingListItem) =
        ItemRow(
            item,
            unsent = ticks[item.id]?.status == TickStatus.Unsent,
            onToggle = { model.toggle(item) },
            onRemove = { model.remove(item) },
        )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 20.dp)) {
        item(key = "header") {
            Header(monday, view, model, gutter.padding(bottom = spacing.m))
        }
        sendingNotice(online, ticks.values, (state as? ListState.Loaded)?.copy)?.let { notice ->
            item(key = "sending") {
                SendingBanner(notice, onSendNow, gutter.padding(bottom = spacing.m))
            }
        }
        error?.let { message ->
            item(key = "error") { Notice(message, gutter.padding(bottom = spacing.m)) }
        }
        when (state) {
            ListState.Failed ->
                item(key = "failed") {
                    ErrorState("Could not load the shopping list.", model::retry, gutter)
                }
            ListState.Loading ->
                item(key = "loading") { Text("Loading…", gutter, color = MimosTheme.colors.muted) }
            ListState.NotGenerated,
            is ListState.Loaded ->
                if (signedIn && list == null) {
                    item(key = "generate") { Generate(model, generating, null, gutter) }
                }
        }
        if (view != null) {
            if (view.total == 0) {
                item(key = "empty") {
                    EmptyState("Nothing to buy", "Plan some meals, or add an item.", gutter)
                }
            }
            for (aisle in view.aisles) {
                item(key = "aisle-${aisle.category}") { AisleHeading(aisle) }
                items(aisle.items, key = { it.id }) { Shown(it) }
            }
            if (view.basket.isNotEmpty()) {
                item(key = "basket") {
                    BasketHeading(view.basket.size, basketOpen) { basketOpen = !basketOpen }
                }
                if (basketOpen) items(view.basket, key = { it.id }) { Shown(it) }
            }
        }
        if (signedIn && list != null) {
            item(key = "add") { AddItemForm(model, gutter.padding(top = spacing.xl)) }
            item(key = "regenerate") {
                Generate(model, generating, list.generatedAt, gutter.padding(top = spacing.xl))
            }
        }
    }
}

@Composable
private fun Header(monday: LocalDate, view: BasketView?, model: ShoppingModel, modifier: Modifier) {
    val spacing = MimosTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Label("Week of ${dayLabel(monday)}")
                Text("Shopping list", style = MimosTheme.typography.screenTitle)
            }
            if (view != null && view.total > 0) {
                Text(
                    "${view.basket.size} of ${view.total} ticked",
                    style = MimosTheme.typography.small,
                    color = MimosTheme.colors.muted,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Button(
                "←",
                model::previousWeek,
                kind = ButtonKind.Secondary,
                small = true,
                description = "Previous week",
            )
            Button("This week", model::thisWeek, kind = ButtonKind.Secondary, small = true)
            Button(
                "→",
                model::nextWeek,
                kind = ButtonKind.Secondary,
                small = true,
                description = "Next week",
            )
        }
    }
}

@Composable
private fun Generate(
    model: ShoppingModel,
    generating: Boolean,
    generatedAt: Instant?,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
        Button(
            when {
                generating -> "Generating…"
                generatedAt == null -> "Generate from this week's plan"
                else -> "Regenerate"
            },
            model::generate,
            Modifier.fillMaxWidth(),
            kind = if (generatedAt == null) ButtonKind.Primary else ButtonKind.Secondary,
            enabled = !generating,
        )
        Text(
            generatedAt?.let(::generatedText)
                ?: "No list for this week yet. Generate one from the plan.",
            style = MimosTheme.typography.small,
            color = MimosTheme.colors.muted,
        )
    }
}

@Composable
private fun AisleHeading(aisle: Aisle) {
    val spacing = MimosTheme.spacing
    Column(Modifier.padding(top = 18.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .semantics(mergeDescendants = true) { heading() }
                .padding(horizontal = spacing.page)
                .padding(bottom = 6.dp)
        ) {
            Label(aisle.category, Modifier.weight(1f))
            Label("${aisle.items.size} left")
        }
        Rule()
    }
}

@Composable
private fun BasketHeading(count: Int, open: Boolean, onToggle: () -> Unit) {
    val spacing = MimosTheme.spacing
    Column(Modifier.padding(top = 20.dp)) {
        Rule()
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics(mergeDescendants = true) { heading() }
                .heightIn(min = 48.dp)
                .padding(horizontal = spacing.page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("In the basket · $count", Modifier.weight(1f))
            Text(
                if (open) "Hide" else "Show",
                style = MimosTheme.typography.small,
                color = MimosTheme.colors.muted,
            )
        }
        Rule()
    }
}

/**
 * The shopping list from the No answer page, before the session is renewed: the stored list to
 * tick, whose ticks wait for the session (ADR-0034). Its banner's action is [onRenew], since
 * nothing can be sent before the session is renewed, which then sends them.
 */
@Composable
fun ShoppingWithoutSession(model: ShoppingModel, onBack: () -> Unit, onRenew: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.padding(horizontal = MimosTheme.spacing.page, vertical = MimosTheme.spacing.s)
        ) {
            Button("← Back", onBack, kind = ButtonKind.Secondary, small = true)
        }
        Column(Modifier.weight(1f)) {
            ShoppingScreen(model, onSendNow = onRenew, signedIn = false)
        }
    }
}

private val DECIMAL = KeyboardOptions(keyboardType = KeyboardType.Decimal)

@Composable
private fun AddItemForm(model: ShoppingModel, modifier: Modifier) {
    val fields by model.fields.collectAsState()
    val adding by model.adding.collectAsState()
    val spacing = MimosTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text("Add an item", style = MimosTheme.typography.subheading)
        TextField(
            fields.name,
            { v -> model.edit { it.copy(name = v) } },
            "Item",
            placeholder = "Anything else to buy",
            maxLength = 200,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                fields.quantity,
                { v -> model.edit { it.copy(quantity = v) } },
                "Amount",
                Modifier.weight(1f),
                keyboardOptions = DECIMAL.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { model.add() }),
            )
            SelectField(
                "Unit",
                selected = fields.unit,
                options = ADD_ITEM_UNITS,
                optionLabel = ::unitLabel,
                onSelect = { v -> model.edit { it.copy(unit = v) } },
                modifier = Modifier.weight(1f),
            )
        }
        Button(if (adding) "Adding…" else "Add", model::add, enabled = !adding)
    }
}

@Composable
private fun ItemRow(
    item: ShoppingListItem,
    unsent: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MimosTheme.colors
    val spacing = MimosTheme.spacing
    val amount = formatQuantity(item.quantity, item.unit)
    val name = countedName(item.name, item.quantity, item.unit)
    val text = if (amount.isEmpty()) name else "$amount $name"
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = spacing.page),
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = item.checked,
                        role = Role.Checkbox,
                        onValueChange = { onToggle() },
                    ),
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TickBox(item.checked)
                Text(
                    text,
                    Modifier.weight(1f),
                    style =
                        MimosTheme.typography.tickRow.copy(
                            textDecoration =
                                if (item.checked) TextDecoration.LineThrough
                                else TextDecoration.None
                        ),
                    color = if (item.checked) colors.muted else colors.fg,
                )
            }
            if (unsent) {
                Text("Not sent", style = MimosTheme.typography.small, color = colors.danger)
            } else if (item.added) {
                Text("Added", style = MimosTheme.typography.small, color = colors.muted)
                Button(
                    "Remove",
                    onRemove,
                    kind = ButtonKind.Secondary,
                    small = true,
                    description = "Remove ${item.name}",
                )
            }
        }
        Rule()
    }
}

/** What waits to be sent, or that the list is a stored copy, in plain text with its one action. */
@Composable
private fun SendingBanner(notice: SendingNotice, onAction: () -> Unit, modifier: Modifier) {
    val colors = MimosTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(colors.surface)
            .border(MimosTheme.spacing.hairline, colors.line)
            .padding(MimosTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
    ) {
        Text(notice.text)
        notice.action?.let { label ->
            Button(label, onAction, kind = ButtonKind.Secondary, small = true)
        }
    }
}
