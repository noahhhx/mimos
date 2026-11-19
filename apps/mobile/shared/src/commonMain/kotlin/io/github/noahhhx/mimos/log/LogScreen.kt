package io.github.noahhhx.mimos.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.api.models.DailyLogSummary
import io.github.noahhhx.mimos.api.models.MealLog
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.plan.MEAL_TYPES
import io.github.noahhhx.mimos.plan.dayLabel
import io.github.noahhhx.mimos.plan.formatServings
import io.github.noahhhx.mimos.plan.mealLabel
import io.github.noahhhx.mimos.plan.weekDays
import io.github.noahhhx.mimos.recipes.form.describeRecipe
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.ListRow
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.SelectField
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField
import kotlinx.datetime.LocalDate

/**
 * The Log tab: the week's per-day totals, the selected day's entries, and the form that logs
 * something the plan did not have. It loads again each time it opens.
 */
@Composable
fun LogScreen(model: LogModel) {
    LaunchedEffect(Unit) { model.opened() }
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val monday by model.monday.collectAsState()
    val selected by model.selected.collectAsState()
    val state by model.week.collectAsState()
    val error by model.error.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.l),
    ) {
        item(key = "header") {
            PageHeader(title = "Log", eyebrow = "Week of ${dayLabel(monday)}", modifier = gutter)
        }
        item(key = "weeks") {
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
                Button("← Previous", model::previousWeek, kind = ButtonKind.Secondary, small = true)
                Button("This week", model::thisWeek, kind = ButtonKind.Secondary, small = true)
                Button("Next →", model::nextWeek, kind = ButtonKind.Secondary, small = true)
            }
        }
        error?.let { message -> item(key = "error") { Notice(message, gutter) } }
        when (val current = state) {
            LogWeekState.Loading ->
                item(key = "loading") {
                    Text("Loading…", gutter, color = MimosTheme.colors.muted)
                }
            LogWeekState.Failed ->
                item(key = "failed") {
                    ErrorState("Could not load your log.", model::retry, gutter)
                }
            is LogWeekState.Loaded -> {
                val week = current.week
                item(key = "table") { WeekTable(week, selected, model::selectDay) }
                item(key = "day") {
                    Text(
                        dayLabel(selected),
                        gutter.padding(top = MimosTheme.spacing.l),
                        style = MimosTheme.typography.heading,
                    )
                }
                val logs = week.logsOn(selected)
                if (logs.isEmpty()) {
                    item(key = "nothing") {
                        Text(
                            "Nothing logged. Plan a meal and tap “Log”, or add one below.",
                            gutter,
                            color = MimosTheme.colors.muted,
                        )
                    }
                } else {
                    items(logs, key = { it.id }) { log -> LogRow(log) { model.delete(log) } }
                }
                item(key = "form") { LogForm(model, gutter.padding(top = MimosTheme.spacing.l)) }
            }
        }
    }
}

/** Figures line up down the columns. */
private const val TABULAR = "tnum"

/** Each day of the week with its totals; tapping one shows and logs its meals. */
@Composable
private fun WeekTable(week: LogWeek, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val colors = MimosTheme.colors
    val spacing = MimosTheme.spacing
    val cell = MimosTheme.typography.small.copy(fontFeatureSettings = TABULAR)
    Column(Modifier.fillMaxWidth()) {
        Text(
            "This week, per day",
            Modifier.padding(horizontal = spacing.page).padding(bottom = spacing.s),
            style = MimosTheme.typography.heading,
        )
        TableRow(Modifier.padding(vertical = spacing.s)) {
            for ((index, heading) in HEADINGS.withIndex()) {
                Label(heading, Modifier.weight(columnWeight(index)))
            }
        }
        Rule()
        for (day in weekDays(week.monday)) {
            val cells = dayCells(week.summaryOf(day))
            val isSelected = day == selected
            val edge = colors.accent
            val edgeWidth = spacing.noticeEdge
            TableRow(
                Modifier.then(
                        if (isSelected) {
                            Modifier.background(colors.hover).drawBehind {
                                drawRect(edge, size = Size(edgeWidth.toPx(), size.height))
                            }
                        } else Modifier
                    )
                    .clickable(role = Role.Button) { onSelect(day) }
                    .semantics(mergeDescendants = true) {
                        this.selected = isSelected
                        contentDescription =
                            "${dayLabel(day)}: ${cells[0]} kcal, ${cells[1]} protein, " +
                                "${cells[2]} carbs, ${cells[3]} fat"
                    }
                    .heightIn(min = 44.dp)
                    .padding(vertical = spacing.s)
            ) {
                Text(dayLabel(day), Modifier.weight(columnWeight(0)), style = cell)
                for ((index, value) in cells.withIndex()) {
                    Text(value, Modifier.weight(columnWeight(index + 1)), style = cell)
                }
            }
            Rule()
        }
        Text(
            "Tap a day to see and add its meals.",
            Modifier.padding(horizontal = spacing.page, vertical = spacing.s),
            style = MimosTheme.typography.small,
            color = colors.muted,
        )
    }
}

/** Kcal heads its column, so its cells are bare numbers: "1235 kcal" does not fit a phone's. */
private val HEADINGS = listOf("Day", "Kcal", "Protein", "Carbs", "Fat")

private fun dayCells(summary: DailyLogSummary?): List<String> {
    val nutrition = summary?.let { Nutrition(it.calories, it.proteinG, it.carbsG, it.fatG) }
    val (kcal, protein, carbs, fat) = nutritionCells(nutrition)
    return listOf(kcal.removeSuffix(" kcal"), protein, carbs, fat)
}

/** The day's column is wider: "Wed 19 Nov" against "1235". */
private fun columnWeight(index: Int): Float = if (index == 0) 1.2f else 1f

@Composable
private fun TableRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = MimosTheme.spacing.page),
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** One logged meal: its meal, what it was, its servings when a recipe, its calories, Delete. */
@Composable
private fun LogRow(log: MealLog, onDelete: () -> Unit) {
    val colors = MimosTheme.colors
    val spacing = MimosTheme.spacing
    val servings = if (log.recipeId != null) "(${formatServings(log.servings)}) " else ""
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spacing.page, vertical = spacing.s),
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(mealLabel(log.mealType))
                Text(log.description)
                Text(
                    servings + formatKcal(log.nutrition.calories),
                    style = MimosTheme.typography.small,
                    color = colors.muted,
                )
            }
            Button(
                "Delete",
                onDelete,
                kind = ButtonKind.Secondary,
                small = true,
                description = "Delete ${log.description}",
            )
        }
        Rule()
    }
}

private val DECIMAL = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/** "Log something else": a recipe in servings, a food by amount, or anything else by hand. */
@Composable
private fun LogForm(model: LogModel, modifier: Modifier) {
    val form by model.form.collectAsState()
    val choices by model.choices.collectAsState()
    val adding by model.adding.collectAsState()
    val selected by model.selected.collectAsState()
    val spacing = MimosTheme.spacing
    val what = form.what
    val text = whatText(what)
    // Offered while typing: once one is picked, What holds it and the list goes.
    val options =
        remember(what, choices) {
            if (what is LogWhat.Custom) choices.search(text) else emptyList()
        }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text("Log something else", style = MimosTheme.typography.heading)
        TextField(
            text,
            model::type,
            "What",
            placeholder = "Search recipes and ingredients, or type anything",
            maxLength = 200,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        if (options.isNotEmpty()) {
            Column {
                for (option in options) {
                    when (option) {
                        is LogWhat.Recipe ->
                            ListRow(
                                option.recipe.title,
                                detail = describeRecipe(option.recipe),
                                onClick = { model.pick(option) },
                            )
                        is LogWhat.Ingredient ->
                            ListRow(
                                option.entry.name,
                                detail = describeIngredient(option.entry),
                                onClick = { model.pick(option) },
                            )
                    }
                }
            }
        }
        SelectField(
            "Meal",
            selected = form.mealType,
            options = MEAL_TYPES,
            optionLabel = ::mealLabel,
            onSelect = model::chooseMeal,
        )
        val measured = measuredOf(what)
        when {
            what is LogWhat.Recipe -> {
                TextField(
                    form.servings,
                    model::editServings,
                    "Servings",
                    keyboardOptions = DECIMAL.copy(imeAction = ImeAction.Done),
                )
                Status(
                    parseServings(form.servings)?.let {
                        describeTotal(recipeTotal(what.recipe, it))
                    } ?: "Servings run from 0.1 to 100."
                )
            }
            measured != null -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TextField(
                        form.amount.quantity,
                        { v -> model.editAmount { it.copy(quantity = v) } },
                        "Amount",
                        Modifier.weight(1f),
                        keyboardOptions = DECIMAL.copy(imeAction = ImeAction.Done),
                    )
                    SelectField(
                        "Unit",
                        selected = form.amount.unit,
                        options = unitsFor(measured.basis),
                        optionLabel = { logUnitLabel(it) },
                        onSelect = { v -> model.editAmount { it.copy(unit = v) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                Status(
                    parseAmount(form.amount.quantity)?.let {
                        describeTotal(measuredTotal(measured, it, form.amount.unit))
                    } ?: "Enter an amount more than 0."
                )
            }
            else -> NutritionFields(form.nutrition, model::editNutrition)
        }
        Button(
            if (adding) "Logging…" else "Log meal",
            model::add,
            enabled = !adding && form.body(selected) != null,
        )
    }
}

@Composable
private fun Status(text: String) {
    Text(text, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
}

/** A custom log's nutrition, two by two. */
@Composable
private fun NutritionFields(
    fields: NutritionFields,
    onEdit: ((NutritionFields) -> NutritionFields) -> Unit,
) {
    val spacing = MimosTheme.spacing
    @Composable
    fun RowScope.Field(
        label: String,
        value: String,
        set: (NutritionFields, String) -> NutritionFields,
    ) {
        TextField(
            value,
            { v -> onEdit { set(it, v) } },
            label,
            Modifier.weight(1f),
            keyboardOptions = DECIMAL.copy(imeAction = ImeAction.Next),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            Field("Calories", fields.calories) { f, v -> f.copy(calories = v) }
            Field("Protein g", fields.proteinG) { f, v -> f.copy(proteinG = v) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            Field("Carbs g", fields.carbsG) { f, v -> f.copy(carbsG = v) }
            Field("Fat g", fields.fatG) { f, v -> f.copy(fatG = v) }
        }
    }
}
