package io.github.noahhhx.mimos.recipes

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionSource
import io.github.noahhhx.mimos.api.models.RecipeDetail
import io.github.noahhhx.mimos.recipes.pictures.PictureRow
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.KeepScreenOn
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.MimosDialog
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Tag
import io.github.noahhhx.mimos.ui.Text

/** One recipe, as you cook it: ingredients and steps are ticked by tapping them. */
@Composable
fun RecipeDetailScreen(model: RecipesModel, route: RecipeRoute.Recipe, webOrigin: String) {
    val detail by model.detail.collectAsState()
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MimosTheme.spacing.xxl)
    ) {
        Trail(model, route, current = (detail as? RecipeDetailState.Loaded)?.recipe?.title)
        // Until the followed recipe loads, the screen would show the one being left.
        when (val state = detail) {
            RecipeDetailState.Loading -> Text("Loading…", gutter, color = MimosTheme.colors.muted)
            RecipeDetailState.NotFound -> {
                PageHeader("Recipe not found", gutter)
                Text(
                    "It may have been deleted, or it belongs to someone else.",
                    gutter,
                    color = MimosTheme.colors.muted,
                )
            }
            is RecipeDetailState.Failed ->
                ErrorState(state.message, onRetry = model::retryDetail, modifier = gutter)
            is RecipeDetailState.Loaded ->
                if (state.recipe.id == route.id.value) {
                    KeepScreenOn()
                    LoadedRecipe(model, state.recipe, webOrigin)
                } else {
                    Text("Loading…", gutter, color = MimosTheme.colors.muted)
                }
        }
    }
}

/** The way back: "← Recipes", or, mid-trail, every recipe followed to get here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Trail(model: RecipesModel, route: RecipeRoute.Recipe, current: String?) {
    val colors = MimosTheme.colors
    val states by model.cookBook.states.collectAsState()
    Column(
        Modifier.padding(horizontal = MimosTheme.spacing.page)
            .padding(bottom = MimosTheme.spacing.xl)
    ) {
        if (route.trail.isEmpty()) {
            Text(
                "← Recipes",
                Modifier.clickable(role = Role.Button) { model.goToCrumb(-1) }
                    .padding(vertical = MimosTheme.spacing.s),
                color = colors.muted,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
                verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
            ) {
                Crumb("Recipes") { model.goToCrumb(-1) }
                route.trail.forEachIndexed { index, id ->
                    Text("›", color = colors.muted)
                    Crumb(states[id]?.title?.takeIf { it.isNotEmpty() } ?: "Recipe") {
                        model.goToCrumb(index)
                    }
                }
                Text("›", color = colors.muted)
                Text(current ?: "…", color = colors.muted)
            }
        }
    }
}

@Composable
private fun Crumb(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clickable(role = Role.Button, onClick = onClick),
        style = MimosTheme.typography.body.copy(textDecoration = TextDecoration.Underline),
    )
}

private fun whose(recipe: RecipeDetail): String =
    when {
        recipe.isLibrary -> "Library recipe"
        recipe.createdBy != null && !recipe.createdBy.you ->
            "Added by ${recipe.createdBy.displayName}"
        else -> "Your recipe"
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadedRecipe(model: RecipesModel, recipe: RecipeDetail, webOrigin: String) {
    val colors = MimosTheme.colors
    val type = MimosTheme.typography
    val uriHandler = LocalUriHandler.current
    val id = RecipeId(recipe.id)
    val states by model.cookBook.states.collectAsState()
    val deletion by model.deletion.collectAsState()
    val rating by model.rating.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val ticks = (states[id] ?: CookState()).within(recipe.ingredients.size, recipe.steps.size)
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    val origin = webOrigin.trimEnd('/')

    Column(verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xl)) {
        Column(gutter, verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m)) {
            Text(whose(recipe), color = colors.muted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
                verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
            ) {
                if (recipe.isLibrary) {
                    recipe.slug?.let { slug ->
                        Button(
                            "Public page",
                            onClick = { uriHandler.openUri("$origin/recipes/$slug") },
                            kind = ButtonKind.Secondary,
                        )
                    }
                } else {
                    Button("Edit", onClick = model::edit, kind = ButtonKind.Secondary)
                    Button(
                        "Delete",
                        onClick = { confirmDelete = true },
                        kind = ButtonKind.Danger,
                        enabled = deletion != DeleteState.Deleting,
                    )
                }
            }
            if (recipe.isLibrary) {
                Text("Library recipes are read-only.", style = type.small, color = colors.muted)
            }
            (deletion as? DeleteState.Failed)?.let { Notice(it.message) }
        }

        Column(gutter) {
            Text(
                servesLine(recipe.servings, recipe.prepMinutes, recipe.cookMinutes),
                style = type.small,
                color = colors.muted,
            )
            PageHeader(recipe.title, Modifier.padding(top = MimosTheme.spacing.s))
            if (!recipe.isLibrary) {
                StarRating(
                    recipe.rating,
                    onRate = { model.rate(recipe, it) },
                    enabled = rating != RateState.Saving,
                )
                (rating as? RateState.Failed)?.let { Notice(it.message) }
            }
            if (recipe.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
                ) {
                    recipe.tags.forEach { Tag(it) }
                }
            }
        }

        PictureRow(model.pictures, recipe.id, recipe.title, recipe.pictures.map { it.id })

        if (recipe.description.isNotEmpty()) {
            Text(recipe.description, gutter, style = type.lede, color = colors.muted)
        }

        Column {
            Text(
                "Ingredients",
                gutter.padding(bottom = MimosTheme.spacing.s).semantics { heading() },
                style = type.heading,
            )
            recipe.ingredients.forEachIndexed { index, ingredient ->
                IngredientRow(
                    ingredient,
                    checked = index in ticks.ingredients,
                    onToggle = { model.cookBook.toggleIngredient(id, index) },
                    onFollow = model::open,
                )
            }
        }

        Column {
            Text(
                "Method",
                gutter.padding(bottom = MimosTheme.spacing.s).semantics { heading() },
                style = type.heading,
            )
            recipe.steps.forEachIndexed { index, step ->
                StepRow(
                    number = index + 1,
                    step = step,
                    checked = index in ticks.steps,
                    onToggle = { model.cookBook.toggleStep(id, index) },
                )
            }
        }

        if (recipe.nutrition.isKnown()) {
            Column(gutter, verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
                Text(
                    "Per serving",
                    style = type.heading,
                    modifier = Modifier.semantics { heading() },
                )
                PerServing(recipe.nutrition)
                if (recipe.nutritionSource == NutritionSource.INGREDIENTS) {
                    Text("Calculated from the ingredients.", color = colors.muted)
                }
            }
        }
    }

    if (confirmDelete) {
        MimosDialog(
            title = "Delete “${recipe.title}”?",
            onDismiss = { confirmDelete = false },
            actions = {
                Button("Cancel", onClick = { confirmDelete = false }, kind = ButtonKind.Secondary)
                Button(
                    "Delete",
                    onClick = {
                        confirmDelete = false
                        model.delete(recipe)
                    },
                    kind = ButtonKind.Danger,
                )
            },
        ) {
            Text("This cannot be undone.", color = colors.muted)
        }
    }
}

/** Per-serving calories and macros as large figures; an unknown value shows as a dash. */
@Composable
internal fun PerServing(n: Nutrition) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.l),
    ) {
        for ((label, value) in
            listOf(
                "Calories" to figure(n.calories, "kcal"),
                "Protein" to figure(n.proteinG, "g"),
                "Carbs" to figure(n.carbsG, "g"),
                "Fat" to figure(n.fatG, "g"),
            )) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
            ) {
                Label(label)
                Text(value, style = MimosTheme.typography.heading)
            }
        }
    }
}
