package io.github.noahhhx.mimos.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.SelectField
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField
import io.github.noahhhx.mimos.ui.TickBox

/**
 * The Shopping tab: the week's list by aisle, ticked with one thumb in the shop, what is still to
 * buy on top, and below it a form for anything else. The screen stays awake while it is open.
 */
@Composable
fun ShoppingScreen(model: ShoppingModel) {
    KeepScreenOn()
    LaunchedEffect(Unit) { model.ensureLoaded() }
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val monday by model.monday.collectAsState()
    val state by model.state.collectAsState()
    val ticks by model.ticks.collectAsState()
    val generating by model.generating.collectAsState()
    val error by model.error.collectAsState()
    val list = (state as? ListState.Loaded)?.list?.takeIf { it.startDate == monday }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.l),
    ) {
        item(key = "header") {
            PageHeader(
                title = "Shopping list",
                eyebrow = "Week of ${dayLabel(monday)}",
                modifier = gutter,
            )
        }
        item(key = "weeks") {
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
                Button("← Previous", model::previousWeek, kind = ButtonKind.Secondary, small = true)
                Button("This week", model::thisWeek, kind = ButtonKind.Secondary, small = true)
                Button("Next →", model::nextWeek, kind = ButtonKind.Secondary, small = true)
            }
        }
        when (val current = state) {
            ListState.Failed ->
                item(key = "failed") {
                    ErrorState("Could not load the shopping list.", model::retry, gutter)
                }
            ListState.Loading ->
                item(key = "loading") {
                    Text("Loading…", gutter, color = MimosTheme.colors.muted)
                }
            ListState.NotGenerated,
            is ListState.Loaded ->
                item(key = "generate") {
                    Column(
                        gutter,
                        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
                    ) {
                        val fresh = current == ListState.NotGenerated
                        Button(
                            when {
                                generating -> "Generating…"
                                fresh -> "Generate from this week's plan"
                                else -> "Regenerate"
                            },
                            model::generate,
                            enabled = !generating,
                        )
                        Text(
                            if (list == null)
                                "No list for this week yet. Generate one from the plan."
                            else generatedText(list.generatedAt),
                            style = MimosTheme.typography.small,
                            color = MimosTheme.colors.muted,
                        )
                    }
                }
        }
        error?.let { message -> item(key = "error") { Notice(message, gutter) } }
        if (list != null) {
            if (list.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        "Nothing to buy",
                        "Plan some meals, or add an item.",
                        gutter,
                    )
                }
            }
            val shown =
                list.items.map { item ->
                    ticks[item.id]?.let { item.copy(checked = it.checked) } ?: item
                }
            for (aisle in aislesToShop(shown)) {
                item(key = "aisle-${aisle.category}") {
                    Column {
                        Text(
                            aisle.category,
                            gutter.padding(bottom = MimosTheme.spacing.s),
                            style = MimosTheme.typography.heading,
                        )
                        Rule()
                    }
                }
                items(aisle.items, key = { it.id }) { item ->
                    ItemRow(
                        item,
                        unsent = ticks[item.id]?.unsent == true,
                        onToggle = { model.toggle(item) },
                        onRetry = { model.retryTick(item.id) },
                        onRemove = { model.remove(item) },
                    )
                }
            }
        }
        // Below the list, unlike the web's page: in the shop the list is what the first screen
        // is for.
        if (state != ListState.Loading && state != ListState.Failed) {
            item(key = "add") { AddItemForm(model, gutter.padding(top = MimosTheme.spacing.l)) }
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
        Text("Add an item", style = MimosTheme.typography.heading)
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

/**
 * One item, ticked by tapping anywhere on its row: a big target for a thumb. An added item has its
 * Remove; a tick the server did not get says so, with its retry.
 */
@Composable
private fun ItemRow(
    item: ShoppingListItem,
    unsent: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MimosTheme.colors
    val spacing = MimosTheme.spacing
    val amount = formatQuantity(item.quantity, item.unit)
    val name = countedName(item.name, item.quantity, item.unit)
    val text = if (amount.isEmpty()) name else "$amount $name"
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = spacing.page, vertical = spacing.xs),
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
                        MimosTheme.typography.cook.copy(
                            textDecoration =
                                if (item.checked) TextDecoration.LineThrough
                                else TextDecoration.None
                        ),
                    color = if (item.checked) colors.muted else colors.fg,
                )
            }
            if (unsent) {
                Text("Not sent", style = MimosTheme.typography.small, color = colors.danger)
                Button(
                    "Retry",
                    onRetry,
                    kind = ButtonKind.Secondary,
                    small = true,
                    description = "Send ${item.name} again",
                )
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
