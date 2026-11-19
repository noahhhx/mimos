package io.github.noahhhx.mimos.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.Person
import io.github.noahhhx.mimos.ingredients.NewIngredientPanel
import io.github.noahhhx.mimos.recipes.form.describeEntry
import io.github.noahhhx.mimos.recipes.form.numberText
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.ListRow
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.Tabs
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField
import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate

/**
 * The Plan tab: the week as a list of days, each with its four meals, and, as full screens, a
 * meal's or a day's inspector and the picker. The web's ledger does not fit a phone (ADR-0028).
 * [onOpenRecipe] shows a planned recipe in the Recipes tab.
 */
@Composable
fun PlanScreen(model: PlanModel, onOpenRecipe: (String) -> Unit) {
    val route by model.route.collectAsState()
    val week by model.week.collectAsState()
    LaunchedEffect(Unit) { model.ensureLoaded() }
    val loaded = (week as? WeekState.Loaded)?.week
    when (val current = route) {
        PlanRoute.Week -> WeekScreen(model, week)
        is PlanRoute.Meal -> {
            val entry = loaded?.entries?.find { it.id == current.entryId }
            if (loaded != null && entry != null) {
                MealInspector(model, loaded, entry, onOpenRecipe)
            }
        }
        is PlanRoute.Day -> if (loaded != null) DayInspector(model, loaded, current.date)
        is PlanRoute.Pick -> Picker(model, current)
    }
}

@Composable
private fun WeekScreen(model: PlanModel, week: WeekState) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val monday by model.monday.collectAsState()
    val view by model.view.collectAsState()
    val error by model.error.collectAsState()
    val panels by model.panels.state.collectAsState()
    val notice by model.notice.collectAsState()
    val loaded = (week as? WeekState.Loaded)?.week?.takeIf { it.monday == monday }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.l),
    ) {
        item(key = "header") {
            PageHeader(title = "Plan", eyebrow = "Week of ${dayLabel(monday)}", modifier = gutter)
        }
        item(key = "weeks") {
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
                Button("← Previous", model::previousWeek, kind = ButtonKind.Secondary, small = true)
                Button("This week", model::thisWeek, kind = ButtonKind.Secondary, small = true)
                Button("Next →", model::nextWeek, kind = ButtonKind.Secondary, small = true)
            }
        }
        if (loaded?.shared == true) {
            item(key = "view") {
                Tabs(
                    labels = PlanView.entries.map { it.label },
                    selected = view.ordinal,
                    onSelect = { model.chooseView(PlanView.entries[it]) },
                    modifier = gutter,
                )
            }
        }
        error?.let { message -> item(key = "error") { Notice(message, gutter) } }
        panels
            ?.takeIf { it.monday == monday && it.panels.isNotEmpty() }
            ?.let {
                item(key = "panels") { WeekPanelsGroup(model.panels, it, gutter) }
            }
        // A suggestion applied can take its card away, and what it did still shows.
        if (loaded != null && (loaded.suggestions.isNotEmpty() || notice != null)) {
            item(key = "suggestions") { SuggestionsSection(model, loaded, gutter) }
        }
        when {
            week is WeekState.Failed ->
                item(key = "failed") { ErrorState(week.message, model::retry, gutter) }
            loaded == null ->
                item(key = "loading") {
                    Text("Loading…", gutter, color = MimosTheme.colors.muted)
                }
            else -> {
                val shown = loaded.shown(view)
                val labels = chipLabels(loaded.members.map { it.displayName })
                items(weekDays(monday), key = { it.toString() }) { date ->
                    DaySection(model, loaded, view, date, shown.filter { it.date == date }, labels)
                }
            }
        }
    }
}

@Composable
private fun DaySection(
    model: PlanModel,
    week: PlanWeek,
    view: PlanView,
    date: LocalDate,
    entries: List<MealPlanEntry>,
    chipLabels: List<String>,
) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val colors = MimosTheme.colors
    val today = model.isToday(date)
    val name = if (today) "Today · ${dayLabel(date)}" else dayLabel(date)
    val totals = week.totalsShown(view)
    Column(Modifier.fillMaxWidth()) {
        val planned = if (totals) plannedCalories(week, date) else null
        Row(
            Modifier.fillMaxWidth()
                .then(
                    if (totals) {
                        Modifier.clickable(role = Role.Button) { model.openDay(date) }
                            .semantics {
                                contentDescription = "$name. Planned: ${planned ?: "nothing yet"}"
                            }
                    } else Modifier
                )
                .padding(horizontal = MimosTheme.spacing.page, vertical = MimosTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                Modifier.weight(1f),
                style = MimosTheme.typography.heading,
                color = if (today) colors.accent else colors.fg,
            )
            if (planned != null) {
                Text(planned, style = MimosTheme.typography.small, color = colors.muted)
            }
        }
        Rule(gutter)
        for (mealType in MEAL_TYPES) {
            MealSlot(
                model,
                week,
                date,
                mealType,
                entries.filter { it.mealType == mealType },
                chipLabels,
            )
        }
    }
}

/** "1840 kcal", a dash when the day's meals' calories are unknown, or nothing for no meals. */
private fun plannedCalories(week: PlanWeek, date: LocalDate): String? {
    val day = week.total(date) ?: return null
    if (day.meals == 0) return null
    val calories = day.total.calories ?: return "– kcal"
    return "${calories.roundToLong()} kcal"
}

@Composable
private fun MealSlot(
    model: PlanModel,
    week: PlanWeek,
    date: LocalDate,
    mealType: MealType,
    entries: List<MealPlanEntry>,
    chipLabels: List<String>,
) {
    val colors = MimosTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .padding(start = MimosTheme.spacing.page, end = MimosTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(mealLabel(mealType), Modifier.width(MEAL_LABEL_WIDTH))
        Column(Modifier.weight(1f).padding(vertical = MimosTheme.spacing.xs)) {
            for (entry in entries) {
                val mine = entry.eatenByYou()
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) { model.openMeal(entry) }
                        .padding(vertical = MimosTheme.spacing.xs)
                ) {
                    Text(entry.name(), color = if (mine) colors.fg else colors.muted)
                    Text(
                        entryMeta(entry, week, chipLabels),
                        style = MimosTheme.typography.small,
                        color = colors.muted,
                    )
                }
            }
        }
        Text(
            "+",
            Modifier.clickable(role = Role.Button) { model.openPicker(date, mealType) }
                .semantics {
                    contentDescription = "Add ${mealLabel(mealType)} for ${dayLabel(date)}"
                }
                .padding(horizontal = MimosTheme.spacing.m, vertical = MimosTheme.spacing.s),
            style = MimosTheme.typography.heading,
            color = colors.muted,
        )
    }
}

/** "2 servings", and in a shared household who eats it: "2 servings · SL AB". */
private fun entryMeta(entry: MealPlanEntry, week: PlanWeek, chipLabels: List<String>): String {
    val amount = entry.amountText()
    if (!week.shared) return amount
    val eaters =
        week.members.mapIndexedNotNull { index, member ->
            chipLabels[index].takeIf { entry.diners.any { it.id == member.id } }
        }
    return (listOf(amount) + eaters).joinToString(" · ")
}

/** A full-screen inspector or picker: the way back, then its content. */
@Composable
private fun Inspector(
    back: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.l),
    ) {
        Text(
            back,
            gutter
                .clickable(role = Role.Button, onClick = onBack)
                .padding(vertical = MimosTheme.spacing.s),
            color = MimosTheme.colors.muted,
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealInspector(
    model: PlanModel,
    week: PlanWeek,
    entry: MealPlanEntry,
    onOpenRecipe: (String) -> Unit,
) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val error by model.error.collectAsState()
    val busy by model.busy.collectAsState()
    val what = entry.what()
    Inspector("← Plan", { model.back() }) {
        PageHeader(
            title = entry.name(),
            eyebrow = "${dayLabel(entry.date)} · ${mealLabel(entry.mealType)}",
            modifier = gutter,
        )
        Row(
            gutter.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (what is PlannedWhat.Recipe) "Recipe" else "Food",
                Modifier.weight(1f),
                color = MimosTheme.colors.muted,
            )
            if (what is PlannedWhat.Recipe) {
                Button(
                    "Open recipe",
                    { onOpenRecipe(what.recipeId) },
                    kind = ButtonKind.Secondary,
                    small = true,
                )
            }
        }
        Column(gutter, verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
            when (what) {
                is PlannedWhat.Recipe -> {
                    Label("Servings")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            "−",
                            { model.fewerServings(entry) },
                            kind = ButtonKind.Secondary,
                            small = true,
                            enabled = !busy && entry.servings > PlanModel.MIN_SERVINGS,
                            description = "Fewer servings",
                        )
                        Text(formatServings(entry.servings))
                        Button(
                            "+",
                            { model.moreServings(entry) },
                            kind = ButtonKind.Secondary,
                            small = true,
                            enabled = !busy && entry.servings < PlanModel.MAX_SERVINGS,
                            description = "More servings",
                        )
                    }
                }
                is PlannedWhat.Food -> FoodAmount(model, entry, what, busy)
            }
        }
        if (week.shared) {
            Column(gutter, verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
                Label("Who eats it")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
                    verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
                ) {
                    for (member in week.members) {
                        DinerToggle(model, entry, member, busy)
                    }
                }
            }
        }
        error?.let { Notice(it, gutter) }
        Button(
            "Remove from plan",
            { model.remove(entry) },
            gutter,
            kind = ButtonKind.Danger,
            enabled = !busy,
            description = "Remove ${entry.name()}",
        )
    }
}

@Composable
private fun DinerToggle(model: PlanModel, entry: MealPlanEntry, member: Person, busy: Boolean) {
    val eats = entry.diners.any { it.id == member.id }
    val last = eats && entry.diners.size == 1
    Button(
        member.displayName,
        { model.toggleDiner(entry, member.id) },
        kind = if (eats) ButtonKind.Primary else ButtonKind.Secondary,
        small = true,
        enabled = !busy && !last,
        description = if (member.you) "${member.displayName} (you)" else member.displayName,
        pressed = eats,
    )
}

/** A planned food's amount, saved from the keyboard's Done or the Save button beside it. */
@Composable
private fun FoodAmount(
    model: PlanModel,
    entry: MealPlanEntry,
    what: PlannedWhat.Food,
    busy: Boolean,
) {
    var text by remember(entry.id, entry.servings) { mutableStateOf(numberText(entry.servings)) }
    val parsed = parseFoodAmount(text)
    val changed = parsed != null && parsed != entry.servings
    Row(
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            label = "Amount (${foodUnitLabel(what.food.unit, parsed ?: entry.servings)})",
            modifier = Modifier.weight(1f),
            description = "Amount of ${what.food.name}",
            error = if (parsed == null) "More than 0 and at most 10,000." else null,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { model.changeAmount(entry, text) }),
        )
        if (changed) {
            Button("Save", { model.changeAmount(entry, text) }, small = true, enabled = !busy)
        }
    }
}

@Composable
private fun DayInspector(model: PlanModel, week: PlanWeek, date: LocalDate) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val day = week.total(date)
    val eyebrow = if (model.isToday(date)) "Today · ${dayLabel(date)}" else dayLabel(date)
    Inspector("← Plan", { model.back() }) {
        PageHeader(title = weekdayName(date), eyebrow = eyebrow, modifier = gutter)
        Column(gutter, verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
            Label("Planned")
            PlannedFigures(day?.takeIf { it.meals > 0 }?.total)
            day?.let { withoutNutritionNote(it) }
                ?.let {
                    Text(it, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
                }
        }
    }
}

/** The day's four planned values side by side, each a dash when unknown. */
@Composable
private fun PlannedFigures(total: Nutrition?) {
    fun round(value: Double?, unit: String = "") = value?.let { "${it.roundToLong()}$unit" } ?: "–"
    val figures =
        listOf(
            "kcal" to round(total?.calories),
            "Protein" to round(total?.proteinG, " g"),
            "Carbs" to round(total?.carbsG, " g"),
            "Fat" to round(total?.fatG, " g"),
        )
    Row(Modifier.fillMaxWidth()) {
        for ((label, value) in figures) {
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
            ) {
                Text(label, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
                Text(value, style = MimosTheme.typography.heading)
            }
        }
    }
}

@Composable
private fun Picker(model: PlanModel, slot: PlanRoute.Pick) {
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val picker by model.picker.collectAsState()
    val error by model.error.collectAsState()
    val busy by model.busy.collectAsState()
    val title = "${mealLabel(slot.mealType)} · ${dayLabel(slot.date)}"
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
    ) {
        item(key = "back") {
            Text(
                "← Plan",
                gutter
                    .clickable(role = Role.Button) { model.back() }
                    .padding(vertical = MimosTheme.spacing.s),
                color = MimosTheme.colors.muted,
            )
        }
        item(key = "title") { PageHeader(title = "Add a meal", eyebrow = title, modifier = gutter) }
        item(key = "search") {
            TextField(
                value = picker.query,
                onValueChange = model::search,
                label = "Search recipes and ingredients",
                modifier = gutter,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        error?.let { message -> item(key = "error") { Notice(message, gutter) } }
        val adding = picker.adding
        val recipes = picker.recipes
        when {
            adding != null ->
                item(key = "new-ingredient") {
                    NewIngredientPanel(
                        adding,
                        onEdit = model::editNewIngredient,
                        onSave = model::saveNewIngredient,
                        onCancel = model::cancelNewIngredient,
                        saveLabel = "Save and plan",
                        enabled = !busy,
                        modifier = gutter,
                    )
                }
            recipes == null ->
                item(key = "searching") {
                    Text("Searching…", gutter, color = MimosTheme.colors.muted)
                }
            recipes.isEmpty() && picker.foods.isEmpty() ->
                item(key = "none") {
                    Text("Nothing matches.", gutter, color = MimosTheme.colors.muted)
                }
            else -> {
                items(recipes, key = { "recipe:${it.id}" }) { recipe ->
                    val whose = if (recipe.isLibrary) " · library" else ""
                    ListRow(
                        recipe.title,
                        detail = "${formatServings(recipe.servings.toDouble())}$whose",
                        onClick = { if (!busy) model.plan(recipe) },
                    )
                }
                items(picker.foods, key = { "food:${it.slug}" }) { food ->
                    val whose = if (food.isShared) "Ingredient" else "Your ingredient"
                    ListRow(
                        food.name,
                        detail = "$whose · ${describeEntry(food)}",
                        onClick = { if (!busy) model.plan(food) },
                    )
                }
            }
        }
        if (picker.offersNewIngredient) {
            item(key = "add-ingredient") {
                ListRow(
                    "+ Add “${picker.query.trim()}” as a new ingredient",
                    onClick = model::startNewIngredient,
                )
            }
        }
    }
}

/** Wide enough for "BREAKFAST" in the label style's letter spacing on one line. */
private val MEAL_LABEL_WIDTH = 104.dp
