package io.github.noahhhx.mimos.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.noahhhx.mimos.api.models.DailyLogSummary
import io.github.noahhhx.mimos.api.models.FuelTarget
import io.github.noahhhx.mimos.api.models.MealLog
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.barcode.ScanButton
import io.github.noahhhx.mimos.barcode.ScanSheet
import io.github.noahhhx.mimos.barcode.openFoodFactsUrl
import io.github.noahhhx.mimos.fuel.FUEL_LABEL
import io.github.noahhhx.mimos.plan.MEAL_TYPES
import io.github.noahhhx.mimos.plan.dayLabel
import io.github.noahhhx.mimos.plan.formatServings
import io.github.noahhhx.mimos.plan.mealLabel
import io.github.noahhhx.mimos.plan.weekDays
import io.github.noahhhx.mimos.recipes.form.describeRecipe
import io.github.noahhhx.mimos.ui.BottomSheet
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.ListRow
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.SelectField
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField
import kotlinx.datetime.LocalDate

/**
 * The Log tab: one day at a time, today first, with its totals and entries. The form that logs
 * something the plan did not have opens over it from the bar along the bottom, and the week's
 * per-day totals open behind a link. It loads again each time it opens.
 */
@Composable
fun LogScreen(model: LogModel) {
    LaunchedEffect(Unit) { model.opened() }
    val spacing = MimosTheme.spacing
    val gutter = Modifier.padding(horizontal = spacing.page)
    val selected by model.selected.collectAsState()
    val state by model.week.collectAsState()
    val error by model.error.collectAsState()
    val formOpen by model.formOpen.collectAsState()
    var weekOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = spacing.xl, bottom = spacing.l),
        ) {
            item(key = "pager") {
                DayPager(selected, model.today(), model::previousDay, model::nextDay, gutter)
            }
            error?.let { message ->
                item(key = "error") { Notice(message, gutter.padding(top = spacing.m)) }
            }
            when (val current = state) {
                LogWeekState.Loading ->
                    item(key = "loading") {
                        Text(
                            "Loading…",
                            gutter.padding(top = spacing.l),
                            color = MimosTheme.colors.muted,
                        )
                    }
                LogWeekState.Failed ->
                    item(key = "failed") {
                        ErrorState(
                            "Could not load your log.",
                            model::retry,
                            gutter.padding(top = spacing.l),
                        )
                    }
                is LogWeekState.Loaded -> {
                    val week = current.week
                    item(key = "totals") {
                        Column {
                            DayTotals(week, selected, gutter.padding(vertical = spacing.m))
                            Rule()
                        }
                    }
                    val logs = week.logsOn(selected)
                    if (logs.isEmpty()) {
                        item(key = "nothing") {
                            Text(
                                "Nothing logged. Plan a meal and tap “Log”, or tap “Log something else”.",
                                gutter.padding(top = spacing.l),
                                color = MimosTheme.colors.muted,
                            )
                        }
                    } else {
                        items(logs, key = { it.id }) { log -> LogRow(log) { model.delete(log) } }
                    }
                    item(key = "week") {
                        Text(
                            "Week totals →",
                            Modifier.fillMaxWidth()
                                .clickable(role = Role.Button) { weekOpen = true }
                                .heightIn(min = 48.dp)
                                .padding(horizontal = spacing.page, vertical = spacing.m),
                            style =
                                MimosTheme.typography.small.copy(
                                    textDecoration = TextDecoration.Underline
                                ),
                            color = MimosTheme.colors.muted,
                        )
                    }
                }
            }
        }
        Rule()
        Row(
            Modifier.fillMaxWidth()
                .background(MimosTheme.colors.bg)
                .padding(horizontal = spacing.page, vertical = spacing.m),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Button("+ Log something else", model::openForm, Modifier.weight(1f))
            Button(
                "Scan",
                model.scanner::open,
                kind = ButtonKind.Secondary,
                description = "Scan a barcode",
            )
        }
    }
    if (weekOpen) {
        BottomSheet(onDismiss = { weekOpen = false }) {
            WeekSheet(model, state, selected) { day ->
                model.selectDay(day)
                weekOpen = false
            }
        }
    }
    if (formOpen) {
        BottomSheet(onDismiss = model::closeForm) {
            LogForm(model, Modifier.padding(horizontal = spacing.page, vertical = spacing.l))
        }
    }
    ScanSheet(model.scanner)
}

@Composable
private fun DayPager(
    day: LocalDate,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Button("‹", onPrevious, kind = ButtonKind.Secondary, description = "Previous day")
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label(dayEyebrow(day, today))
            Text(dayLabel(day), style = MimosTheme.typography.screenTitle)
        }
        Button("›", onNext, kind = ButtonKind.Secondary, description = "Next day")
    }
}

@Composable
private fun DayTotals(week: LogWeek, day: LocalDate, modifier: Modifier) {
    val colors = MimosTheme.colors
    val spacing = MimosTheme.spacing
    val tiny = MimosTheme.typography.small.copy(fontSize = 11.sp, fontFeatureSettings = TABULAR)
    val totals =
        dayTotals(
            week.summaryOf(day)?.let(::summaryNutrition),
            week.targets[day]?.let(::targetNutrition),
        )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
        for (total in totals) {
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    total.label.uppercase(),
                    style = MimosTheme.typography.label.copy(fontSize = 11.sp),
                    color = colors.muted,
                )
                Text(
                    total.value,
                    style = MimosTheme.typography.subheading.copy(fontFeatureSettings = TABULAR),
                )
                total.share?.let { share ->
                    Box(Modifier.fillMaxWidth().height(6.dp).background(colors.line)) {
                        Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(colors.accent))
                    }
                }
                total.of?.let { Text("of $it", style = tiny, color = colors.muted) }
            }
        }
    }
}

@Composable
private fun WeekSheet(
    model: LogModel,
    state: LogWeekState,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val spacing = MimosTheme.spacing
    Column(
        Modifier.padding(vertical = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Row(
            Modifier.padding(horizontal = spacing.page),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
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
        when (state) {
            is LogWeekState.Loaded -> WeekTable(state.week, selected, onSelect)
            LogWeekState.Loading ->
                Text(
                    "Loading…",
                    Modifier.padding(horizontal = spacing.page),
                    color = MimosTheme.colors.muted,
                )
            LogWeekState.Failed ->
                ErrorState(
                    "Could not load your log.",
                    model::retry,
                    Modifier.padding(horizontal = spacing.page),
                )
        }
    }
}

private fun summaryNutrition(summary: DailyLogSummary) =
    Nutrition(summary.calories, summary.proteinG, summary.carbsG, summary.fatG)

private fun targetNutrition(target: FuelTarget) =
    Nutrition(
        target.calories.toDouble(),
        target.proteinG.toDouble(),
        target.carbsG.toDouble(),
        target.fatG.toDouble(),
    )

/** Figures line up down the columns. */
private const val TABULAR = "tnum"

@Composable
private fun WeekTable(week: LogWeek, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val colors = MimosTheme.colors
    val spacing = MimosTheme.spacing
    val cell = MimosTheme.typography.small.copy(fontFeatureSettings = TABULAR)
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Week of ${dayLabel(week.monday)}",
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
            val isSelected = day == selected
            val edge = colors.accent
            val edgeWidth = spacing.noticeEdge
            @Composable
            fun DayRow(label: String, name: String, cells: List<String>, muted: Boolean) =
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
                                "$name: ${cells[0]} kcal, ${cells[1]} protein, " +
                                    "${cells[2]} carbs, ${cells[3]} fat"
                        }
                        .heightIn(min = 44.dp)
                        .padding(vertical = spacing.s)
                ) {
                    val shade = if (muted) colors.muted else colors.fg
                    Text(label, Modifier.weight(columnWeight(0)), style = cell, color = shade)
                    for ((index, value) in cells.withIndex()) {
                        Text(
                            value,
                            Modifier.weight(columnWeight(index + 1)),
                            style = cell,
                            color = shade,
                        )
                    }
                }
            DayRow(
                dayLabel(day),
                dayLabel(day),
                bareCells(week.summaryOf(day)?.let(::summaryNutrition)),
                muted = false,
            )
            week.targets[day]?.let { target ->
                DayRow(
                    FUEL_LABEL,
                    "$FUEL_LABEL, ${dayLabel(day)}",
                    bareCells(targetNutrition(target)),
                    muted = true,
                )
            }
            Rule()
        }
        Text(
            "Tap a day to see its meals.",
            Modifier.padding(horizontal = spacing.page, vertical = spacing.s),
            style = MimosTheme.typography.small,
            color = colors.muted,
        )
    }
}

/** Kcal heads its column, so its cells are bare numbers: "1235 kcal" does not fit a phone's. */
private val HEADINGS = listOf("Day", "Kcal", "Protein", "Carbs", "Fat")

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
    val error by model.error.collectAsState()
    val spacing = MimosTheme.spacing
    val what = form.what
    val text = whatText(what)
    // Offered while typing: once one is picked, What holds it and the list goes.
    val options =
        remember(what, choices) {
            if (what is LogWhat.Custom) choices.search(text) else emptyList()
        }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            "Log something else · ${dayLabel(selected)}",
            style = MimosTheme.typography.heading,
        )
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
        ScanButton(model.scanner)
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
                if (what is LogWhat.Product) ProductSource(what, measured)
            }
            else -> NutritionFields(form.nutrition, model::editNutrition)
        }
        error?.let { Notice(it) }
        Button(
            if (adding) "Logging…" else "Log meal",
            model::add,
            Modifier.fillMaxWidth(),
            enabled = !adding && form.body(selected) != null,
        )
        Button("Cancel", model::closeForm, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

/**
 * A scanned product's serving and where its figures come from: Open Food Facts' data is ODbL, which
 * asks for the attribution (ADR-0025).
 */
@Composable
private fun ProductSource(what: LogWhat.Product, food: Measured) {
    val uriHandler = LocalUriHandler.current
    servingHint(food)?.let { Status(it) }
    Text(
        "From Open Food Facts",
        Modifier.clickable(role = Role.Button) {
            uriHandler.openUri(openFoodFactsUrl(what.product.barcode))
        },
        style = MimosTheme.typography.small.copy(textDecoration = TextDecoration.Underline),
        color = MimosTheme.colors.muted,
    )
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
