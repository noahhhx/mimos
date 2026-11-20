package io.github.noahhhx.mimos.kitchen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.api.models.RecipeDetail
import io.github.noahhhx.mimos.api.models.ShoppingList
import io.github.noahhhx.mimos.plan.PlannedWhat
import io.github.noahhhx.mimos.plan.amountText
import io.github.noahhhx.mimos.plan.dayLabel
import io.github.noahhhx.mimos.plan.name
import io.github.noahhhx.mimos.plan.weekdayName
import io.github.noahhhx.mimos.plan.what
import io.github.noahhhx.mimos.recipes.countedName
import io.github.noahhhx.mimos.recipes.form.numberText
import io.github.noahhhx.mimos.recipes.formatQuantity
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.NoticeKind
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TickBox
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

/** Where the home's links go: each block leads to its own tab. */
class KitchenActions(
    val onOpenRecipe: (String) -> Unit,
    /** The Plan tab's picker for tonight's dinner. */
    val onPlanTonight: () -> Unit,
    /** The Plan tab at this meal, to change or remove it. */
    val onSwapMeal: (MealPlanEntry) -> Unit,
    val onOpenShopping: () -> Unit,
)

/**
 * The Kitchen tab, the first screen after sign-in: the greeting, then tonight's meal, what is left
 * to buy, a plugin's thought for an open evening, and the week's dinners. On a phone, tonight comes
 * straight after the greeting, where the web's narrow layout puts the week first.
 */
@Composable
fun KitchenScreen(model: KitchenModel, actions: KitchenActions) {
    LaunchedEffect(Unit) { model.open() }
    val time by model.time.collectAsState()
    val week by model.week.collectAsState()
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val today = time.date
    val loaded = (week as? Block.Loaded)?.value?.takeIf { it.monday == model.monday }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xxl),
    ) {
        PageHeader(title = greeting(time.hour), eyebrow = longDateLabel(today), modifier = gutter)
        when {
            week == Block.Failed ->
                ErrorState("Could not load this week's plan.", model::open, gutter)
            loaded == null -> Text("Loading…", gutter, color = MimosTheme.colors.muted)
            else -> Tonight(model, pickTonight(loaded.entries, today), actions, gutter)
        }
        StillToBuyBlock(model, actions, gutter)
        if (loaded != null) {
            ThoughtBlock(model, pickThought(loaded.suggestions, loaded.entries, today), gutter)
            ThisWeek(loaded, today, actions)
        }
    }
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Label(text, modifier.semantics { heading() })
}

@Composable
private fun Tonight(
    model: KitchenModel,
    tonight: TonightPick?,
    actions: KitchenActions,
    modifier: Modifier,
) {
    val recipe by model.recipe.collectAsState()
    val spacing = MimosTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        SectionHeading(tonight?.label?.text ?: TonightLabel.Tonight.text)
        if (tonight == null) {
            Text("Nothing planned for tonight", style = MimosTheme.typography.heading)
            Button("Plan tonight", actions.onPlanTonight)
            return@Column
        }
        val entry = tonight.entry
        val what = entry.what()
        Text(entry.name(), style = MimosTheme.typography.heading)
        if (what is PlannedWhat.Recipe) {
            when (val current = recipe) {
                Block.Failed ->
                    Notice("Could not load the recipe's details.", Modifier.fillMaxWidth())
                is Block.Loaded ->
                    if (current.value.id == what.recipeId) {
                        if (current.value.description.isNotBlank()) {
                            Text(current.value.description, color = MimosTheme.colors.muted)
                        }
                        Facts(current.value, entry.servings)
                    }
                Block.Loading -> {}
            }
        } else {
            Text(entry.amountText(), color = MimosTheme.colors.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            if (what is PlannedWhat.Recipe) {
                Button("Start cooking", { actions.onOpenRecipe(what.recipeId) })
            }
            Button("Swap meal", { actions.onSwapMeal(entry) }, kind = ButtonKind.Secondary)
        }
    }
}

/** Tonight's minutes, servings, and kcal per serving, each shown only when known. */
@Composable
private fun Facts(recipe: RecipeDetail, servings: Double) {
    val facts = buildList {
        totalMinutes(recipe.prepMinutes, recipe.cookMinutes)?.let { add("$it" to "minutes") }
        add(numberText(servings) to if (servings == 1.0) "serving" else "servings")
        recipe.nutrition.calories?.let { add("${it.roundToInt()}" to "kcal each") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xl)) {
        for ((value, unit) in facts) {
            Column(Modifier.clearAndSetSemantics { contentDescription = "$value $unit" }) {
                Text(value, style = MimosTheme.typography.heading)
                Text(unit, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
            }
        }
    }
}

@Composable
private fun StillToBuyBlock(model: KitchenModel, actions: KitchenActions, modifier: Modifier) {
    val list by model.list.collectAsState()
    val spacing = MimosTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        val loaded = (list as? Block.Loaded)?.value?.takeIf { it.startDate == model.monday }
        val toBuy = loaded?.let { stillToBuy(it.items) }
        SectionHeading(
            if (toBuy != null && toBuy.total > 0) {
                "Still to buy · ${toBuy.remaining} of ${toBuy.total}"
            } else "Still to buy"
        )
        when {
            list == Block.Failed -> Notice("Could not load the shopping list.")
            list == Block.Loaded(null) -> {
                Text("No list yet.")
                Button(
                    "Make this week's list",
                    actions.onOpenShopping,
                    kind = ButtonKind.Secondary,
                )
            }
            toBuy == null -> Text("Loading…", color = MimosTheme.colors.muted)
            toBuy.total == 0 -> Text("Nothing to buy this week.", color = MimosTheme.colors.muted)
            else -> {
                ToBuyRows(loaded)
                Button("Full list", actions.onOpenShopping, kind = ButtonKind.Secondary)
            }
        }
    }
}

@Composable
private fun ToBuyRows(list: ShoppingList) {
    val colors = MimosTheme.colors
    Column {
        Rule()
        for (item in stillToBuy(list.items).shown) {
            val amount = formatQuantity(item.quantity, item.unit)
            val name = countedName(item.name, item.quantity, item.unit)
            val text = if (amount.isEmpty()) name else "$amount $name"
            Row(
                Modifier.fillMaxWidth()
                    .padding(vertical = MimosTheme.spacing.s)
                    .clearAndSetSemantics {
                        contentDescription = if (item.checked) "Got: $text" else text
                    },
                horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TickBox(item.checked)
                Text(
                    text,
                    style =
                        MimosTheme.typography.body.copy(
                            textDecoration =
                                if (item.checked) TextDecoration.LineThrough
                                else TextDecoration.None
                        ),
                    color = if (item.checked) colors.muted else colors.fg,
                )
            }
            Rule()
        }
    }
}

@Composable
private fun ThoughtBlock(model: KitchenModel, thought: Thought?, modifier: Modifier) {
    val adding by model.adding.collectAsState()
    val notice by model.notice.collectAsState()
    if (thought == null && notice == null) return
    val colors = MimosTheme.colors
    Column(
        modifier.fillMaxWidth().border(MimosTheme.spacing.hairline, colors.line).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
    ) {
        if (thought == null) {
            SectionHeading("Suggestions")
        } else {
            val day = weekdayName(thought.entry.date)
            val suggestion = thought.suggestion
            SectionHeading("A thought for $day")
            Text(suggestion.title, style = MimosTheme.typography.heading)
            suggestion.blurb?.let { Text(it, color = colors.muted) }
            Text("${thought.entry.recipeTitle} would fill the open evening.")
            Text(
                listOfNotNull(suggestion.icon, "from ${suggestion.pluginName}").joinToString(" "),
                style = MimosTheme.typography.small,
                color = colors.muted,
            )
            Button(
                if (adding) "Adding…" else "Add to $day",
                { model.addThought(thought) },
                kind = ButtonKind.Secondary,
                enabled = !adding,
            )
        }
        notice?.let {
            Notice(it.text, kind = if (it.added) NoticeKind.Ok else NoticeKind.Error)
        }
    }
}

@Composable
private fun ThisWeek(
    week: KitchenWeek,
    today: LocalDate,
    actions: KitchenActions,
) {
    val colors = MimosTheme.colors
    val spacing = MimosTheme.spacing
    Column {
        SectionHeading(
            "This week",
            Modifier.padding(horizontal = spacing.page).padding(bottom = spacing.m),
        )
        Rule()
        for ((date, dinner) in weekDinners(week.entries, week.monday)) {
            val isToday = date == today
            val recipeId = (dinner?.what() as? PlannedWhat.Recipe)?.recipeId
            val name = dinner?.let { if (recipeId != null) it.name() else foodLabel(it) }
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(
                        if (recipeId == null) Modifier
                        else
                            Modifier.clickable(role = Role.Button) {
                                actions.onOpenRecipe(recipeId)
                            }
                    )
                    .padding(horizontal = spacing.page, vertical = spacing.s)
                    .clearAndSetSemantics {
                        contentDescription =
                            "${dayLabel(date)}${if (isToday) " (today)" else ""}: ${name ?: "Nothing yet"}"
                    },
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    dayLabel(date),
                    Modifier.width(104.dp),
                    style = MimosTheme.typography.small,
                    color = if (isToday) colors.accent else colors.muted,
                )
                Text(
                    name ?: "Nothing yet",
                    Modifier.weight(1f),
                    color = if (name == null) colors.muted else colors.fg,
                )
            }
            Rule()
        }
    }
}

/** A planned food reads with its amount, as on the web's rail. */
private fun foodLabel(entry: MealPlanEntry): String = "${entry.name()}, ${entry.amountText()}"
