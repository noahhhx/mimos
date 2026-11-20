package io.github.noahhhx.mimos.recipes.form

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.IngredientLineStatus
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionSource
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.ingredients.NewIngredientPanel
import io.github.noahhhx.mimos.ingredients.NutritionFields
import io.github.noahhhx.mimos.recipes.PerServing
import io.github.noahhhx.mimos.recipes.pictures.RecipePictures
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.Choice
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

private val WHOLE = KeyboardOptions(keyboardType = KeyboardType.Number)
private val DECIMAL = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/**
 * The recipe form, as the web's: [title] is "New recipe" or "Edit recipe", and [back] names where
 * cancelling returns to. Every field is emitted from one call site in the one editing branch, so
 * none loses its focus when the state around it changes.
 */
@Composable
fun RecipeFormScreen(
    model: RecipeFormModel,
    pictures: RecipePictures,
    title: String,
    back: String,
    onCancel: () -> Unit,
) {
    val state by model.state.collectAsState()
    val scroll = rememberScrollState()
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = MimosTheme.spacing.xxl)
    ) {
        Text(
            "← $back",
            gutter
                .clickable(role = Role.Button, onClick = onCancel)
                .padding(vertical = MimosTheme.spacing.s),
            color = MimosTheme.colors.muted,
        )
        PageHeader(title, gutter.padding(top = MimosTheme.spacing.l))
        when (val current = state) {
            RecipeFormState.Closed,
            RecipeFormState.Loading -> Text("Loading…", gutter, color = MimosTheme.colors.muted)
            is RecipeFormState.Failed ->
                ErrorState(current.message, onRetry = model::retry, modifier = gutter)
            is RecipeFormState.Editing -> {
                LaunchedEffect(current.errors) {
                    if (current.errors.isNotEmpty()) scroll.scrollTo(0)
                }
                Form(model, current, pictures, onCancel, gutter)
            }
        }
    }
}

@Composable
private fun Form(
    model: RecipeFormModel,
    form: RecipeFormState.Editing,
    pictures: RecipePictures,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val draft = form.draft
    val spacing = MimosTheme.spacing
    val calculated = draft.nutritionSource == NutritionSource.INGREDIENTS
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        if (form.errors.isNotEmpty()) Notice(form.errors.joinToString("\n"))

        TextField(
            draft.title,
            { v -> model.edit { it.copy(title = v) } },
            "Title",
            maxLength = 200,
        )
        TextField(
            draft.description,
            { v -> model.edit { it.copy(description = v) } },
            "Description",
            minLines = 2,
            maxLength = 2000,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                draft.servings,
                { v -> model.edit { it.copy(servings = v) } },
                "Servings",
                Modifier.weight(1f),
                keyboardOptions = WHOLE,
            )
            TextField(
                draft.prepMinutes,
                { v -> model.edit { it.copy(prepMinutes = v) } },
                "Prep minutes",
                Modifier.weight(1f),
                keyboardOptions = WHOLE,
            )
            TextField(
                draft.cookMinutes,
                { v -> model.edit { it.copy(cookMinutes = v) } },
                "Cook minutes",
                Modifier.weight(1f),
                keyboardOptions = WHOLE,
            )
        }
        TextField(
            draft.tags,
            { v -> model.edit { it.copy(tags = v) } },
            "Tags (comma-separated)",
            placeholder = "dinner, vegetarian",
        )

        Heading("Pictures")
        PicturesField(model, form, pictures)

        Heading("Ingredients")
        Text(
            "Search for each ingredient or one of your recipes, or add your own ingredient when it " +
                "isn't there. Measure it in grams, millilitres, spoons, or pieces (a recipe in " +
                "servings or whole) to count it toward nutrition, and leave the amount blank for " +
                "things like salt to taste.",
            style = MimosTheme.typography.small,
            color = MimosTheme.colors.muted,
        )
        draft.ingredients.forEachIndexed { index, row ->
            key(index) { IngredientEditor(model, form, index, row, calculated) }
        }
        Button("+ Ingredient", model::addIngredient, kind = ButtonKind.Secondary)

        Heading("Nutrition per serving")
        Column {
            Choice(
                "Calculate from ingredients",
                calculated,
                {
                    model.edit { it.copy(nutritionSource = NutritionSource.INGREDIENTS) }
                },
            )
            Choice(
                "Enter it myself",
                !calculated,
                {
                    model.edit { it.copy(nutritionSource = NutritionSource.MANUAL) }
                },
            )
        }
        if (calculated) {
            CalculatedNutrition(form.estimate, sent = draft.toEstimateInput()?.rows?.size ?: 0)
        } else {
            NutritionFields(
                listOf(draft.calories, draft.proteinG, draft.carbsG, draft.fatG),
                labels = listOf("Calories", "Protein g", "Carbs g", "Fat g"),
                descriptions = listOf("Calories", "Protein g", "Carbs g", "Fat g"),
                onChange = { field, v ->
                    model.edit {
                        when (field) {
                            0 -> it.copy(calories = v)
                            1 -> it.copy(proteinG = v)
                            2 -> it.copy(carbsG = v)
                            else -> it.copy(fatG = v)
                        }
                    }
                },
            )
        }

        Heading("Steps")
        draft.steps.forEachIndexed { index, step ->
            key(index) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    TextField(
                        step,
                        { model.editStep(index, it) },
                        "Step ${index + 1}",
                        minLines = 2,
                        maxLength = 4000,
                    )
                    Button(
                        "Remove",
                        { model.removeStep(index) },
                        kind = ButtonKind.Secondary,
                        small = true,
                        description = "Remove step ${index + 1}",
                    )
                }
            }
        }
        Button("+ Step", model::addStep, kind = ButtonKind.Secondary)

        Row(
            Modifier.padding(top = spacing.s),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Button(
                when {
                    form.saving -> "Saving…"
                    form.recipeId != null -> "Save changes"
                    else -> "Create recipe"
                },
                model::save,
                enabled = !form.saving && !form.shrinking,
            )
            Button("Cancel", onCancel, kind = ButtonKind.Secondary, enabled = !form.saving)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        Modifier.padding(top = MimosTheme.spacing.s).semantics { heading() },
        style = MimosTheme.typography.heading,
    )
}

/**
 * One ingredient line, stacked for a phone: amount and unit, the name with its search options under
 * it, the note, the row's buttons, then what it counts as and why it may not count.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientEditor(
    model: RecipeFormModel,
    form: RecipeFormState.Editing,
    index: Int,
    row: IngredientRow,
    calculated: Boolean,
) {
    val spacing = MimosTheme.spacing
    val colors = MimosTheme.colors
    val number = index + 1
    val link = linkOf(row, form.bySlug, form.byRecipeId)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Rule()
        Label("Ingredient $number", Modifier.padding(top = spacing.xs))
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                row.quantity,
                { v -> model.editRow(index) { it.copy(quantity = v) } },
                "Amount",
                Modifier.weight(1f),
                description = "Ingredient $number amount",
                placeholder = "to taste",
                keyboardOptions = DECIMAL,
            )
            SelectField(
                "Unit",
                selected = row.unit,
                options = unitChoices(row.unit, row.link is RowLink.Recipe),
                optionLabel = ::unitLabel,
                onSelect = { v -> model.editRow(index) { it.copy(unit = v) } },
                modifier = Modifier.weight(1f),
                description = "Ingredient $number unit",
            )
        }
        IngredientPicker(
            label = "Ingredient $number name",
            name = row.name,
            catalog = form.catalog,
            recipes = form.recipes,
            onType = { model.typeName(index, it) },
            onPick = { model.pick(index, it) },
            onPickRecipe = { model.pick(index, it) },
            onAdd = { model.startNewIngredient(index) },
        )
        TextField(
            row.note,
            { v -> model.editRow(index) { it.copy(note = v) } },
            "Note",
            description = "Ingredient $number note",
            placeholder = "minced",
            maxLength = 200,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Button(
                "Up",
                { model.moveIngredient(index, index - 1) },
                kind = ButtonKind.Secondary,
                small = true,
                enabled = index > 0,
                description = "Move ingredient $number up",
            )
            Button(
                "Down",
                { model.moveIngredient(index, index + 1) },
                kind = ButtonKind.Secondary,
                small = true,
                enabled = index < form.draft.ingredients.lastIndex,
                description = "Move ingredient $number down",
            )
            Button(
                "Remove",
                { model.removeIngredient(index) },
                kind = ButtonKind.Secondary,
                small = true,
                description = "Remove ingredient $number",
            )
        }
        if (link != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(describeLink(link), style = MimosTheme.typography.small, color = colors.muted)
                Button(
                    "Don't count it",
                    { model.dontCount(index) },
                    kind = ButtonKind.Secondary,
                    small = true,
                    description = "Don't count ingredient $number",
                )
            }
        }
        if (calculated) {
            lineHint(form.estimate.statusOf(index), link)?.let {
                Text(it, style = MimosTheme.typography.small, color = colors.muted)
            }
        }
        form.adding
            ?.takeIf { it.row == index }
            ?.let {
                NewIngredientPanel(
                    it.draft,
                    onEdit = model::editNewIngredient,
                    onSave = model::saveNewIngredient,
                    onCancel = model::cancelNewIngredient,
                )
            }
    }
}

private fun Estimate.statusOf(row: Int): IngredientLineStatus? =
    when (this) {
        is Estimate.Ready -> statuses[row]
        is Estimate.Pending -> last?.statuses?.get(row)
        Estimate.None,
        Estimate.Failed -> null
    }

/**
 * A line's name, typed into a search over the ingredients the household can count (ADR-0016) and
 * its own recipes (ADR-0018). The options show under the field while it has focus; picking one
 * links the line, and when nothing fits, the last option adds what was typed as a new ingredient.
 */
@Composable
private fun IngredientPicker(
    label: String,
    name: String,
    catalog: List<CatalogIngredient>,
    recipes: List<RecipeSummary>,
    onType: (String) -> Unit,
    onPick: (CatalogIngredient) -> Unit,
    onPickRecipe: (RecipeSummary) -> Unit,
    onAdd: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    val typed = name.trim()
    val matches = searchCatalog(name, catalog)
    val recipeMatches = searchRecipes(name, recipes)
    val exact =
        matches.any { it.name.lowercase() == typed.lowercase() } ||
            recipeMatches.any { it.title.lowercase() == typed.lowercase() }
    val adds = typed != "" && !exact
    val showing = focused && open && (matches.isNotEmpty() || recipeMatches.isNotEmpty() || adds)
    Column {
        TextField(
            name,
            {
                onType(it)
                open = true
            },
            "Ingredient",
            Modifier.onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) open = true
            },
            description = label,
            placeholder = "Search ingredients",
            maxLength = 200,
        )
        if (showing) {
            Column(
                Modifier.fillMaxWidth().border(MimosTheme.spacing.hairline, MimosTheme.colors.line)
            ) {
                // The household's own recipe is the likelier meaning: their pesto before the shared
                // one.
                for (recipe in recipeMatches) {
                    ListRow(
                        recipe.title,
                        detail = describeRecipe(recipe),
                        onClick = {
                            open = false
                            onPickRecipe(recipe)
                        },
                    )
                }
                for (entry in matches) {
                    ListRow(
                        entry.name,
                        detail = (if (entry.isShared) "" else "Yours · ") + describeEntry(entry),
                        onClick = {
                            open = false
                            onPick(entry)
                        },
                    )
                }
                if (adds) {
                    ListRow(
                        "+ Add “$typed” as a new ingredient",
                        onClick = {
                            open = false
                            onAdd()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculatedNutrition(estimate: Estimate, sent: Int) {
    val colors = MimosTheme.colors
    if (estimate == Estimate.Failed) {
        Text(
            "Nutrition could not be calculated right now. It is calculated again when you save.",
            color = colors.muted,
        )
        return
    }
    val shown =
        when (estimate) {
            is Estimate.Ready -> estimate
            is Estimate.Pending -> estimate.last
            else -> null
        }
    val counted = shown?.statuses?.values?.count { it == IngredientLineStatus.COUNTED } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
        PerServing(shown?.nutrition ?: Nutrition())
        Text(
            if (counted == 0) "Nothing counts yet. Pick what each ingredient counts as."
            else "Counting $counted of $sent ingredients.",
            color = colors.muted,
        )
    }
}
