package io.github.noahhhx.mimos.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.recipes.collections.CollectionActions
import io.github.noahhhx.mimos.recipes.collections.CollectionFormScreen
import io.github.noahhhx.mimos.recipes.collections.collectionGallery
import io.github.noahhhx.mimos.recipes.form.RecipeFormScreen
import io.github.noahhhx.mimos.ui.BottomSheet
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.EmptyState
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.ListRow
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.ScrollingTabs
import io.github.noahhhx.mimos.ui.Tabs
import io.github.noahhhx.mimos.ui.Tag
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField

/**
 * The Recipes tab: the list, the recipe the route names, or the form. [webOrigin] is the instance's
 * web address, where a library recipe's public page is.
 */
@Composable
fun RecipesScreen(model: RecipesModel, webOrigin: String) {
    val route by model.route.collectAsState()
    // Kept above the branch so coming back from a recipe finds the list where it was left.
    val listState = rememberLazyListState()
    when (val current = route) {
        RecipeRoute.List -> RecipeListScreen(model, listState)
        is RecipeRoute.Recipe -> RecipeDetailScreen(model, current, webOrigin)
        RecipeRoute.New ->
            RecipeFormScreen(model.form, model.pictures, "New recipe", "Recipes", model::back)
        is RecipeRoute.Edit ->
            RecipeFormScreen(model.form, model.pictures, "Edit recipe", "Recipe", model::back)
        RecipeRoute.NewTab -> CollectionFormScreen(model, "New tab", model::back)
        is RecipeRoute.EditTab -> CollectionFormScreen(model, "Edit tab", model::back)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeListScreen(model: RecipesModel, listState: LazyListState) {
    val list by model.list.collectAsState()
    val tab by model.tab.collectAsState()
    val query by model.query.collectAsState()
    val sort by model.sort.collectAsState()
    val selectedTags by model.selectedTags.collectAsState()
    val defaulting by model.defaulting.collectAsState()
    var sortOpen by remember { mutableStateOf(false) }
    val loaded = list as? RecipeListState.Loaded
    val collections = loaded?.collections.orEmpty()
    val bar = remember(collections) { tabBar(collections) }
    val shownCollection = (tab as? RecipeTab.Collection)?.let { loaded?.collection(it.id) }
    val tabRecipes = loaded?.of(tab)
    val available = remember(tabRecipes) { recipeTags(tabRecipes ?: emptyList()) }
    val active = remember(available, selectedTags) { activeTags(available, selectedTags) }
    val shown =
        remember(tabRecipes, sort, active) {
            tabRecipes?.let { orderRecipes(it, sort, active) }
        }
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    LaunchedEffect(Unit) { model.ensureLoaded() }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.l),
    ) {
        item(key = "header") {
            PageHeader(
                title = "Recipes",
                eyebrow = "Your recipe book",
                modifier = gutter,
                actions = {
                    Button("+ New recipe", onClick = model::newRecipe)
                    Button("+ New tab", onClick = model::newTab, kind = ButtonKind.Secondary)
                },
            )
        }
        item(key = "tabs") {
            val labels = bar.map { tabLabel(it, loaded) }
            val selected = bar.indexOf(tab).coerceAtLeast(0)
            if (collections.isEmpty()) {
                Tabs(labels, selected, { model.selectTab(bar[it]) }, gutter)
            } else {
                ScrollingTabs(
                    labels,
                    selected,
                    { model.selectTab(bar[it]) },
                    contentPadding = MimosTheme.spacing.page,
                )
            }
        }
        if (tab == RecipeTab.Gallery) {
            collectionGallery(collections, model, gutter)
            return@LazyColumn
        }
        if (shownCollection != null) {
            item(key = "tab-actions") {
                CollectionActions(shownCollection, defaulting, model, gutter)
            }
        }
        item(key = "search") {
            TextField(
                value = query,
                onValueChange = model::search,
                label = "Search recipes",
                modifier = gutter,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { model.refresh() }),
            )
        }
        item(key = "sort") {
            Row(
                gutter.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("Sort by")
                Button(
                    sort.label,
                    onClick = { sortOpen = true },
                    kind = ButtonKind.Secondary,
                    small = true,
                )
            }
        }
        if (available.isNotEmpty()) {
            item(key = "tags") {
                // One scrolling line: twenty tags would otherwise push the recipes off a phone.
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MimosTheme.spacing.page),
                    horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
                ) {
                    if (active.isNotEmpty()) {
                        item(key = "clear") {
                            Button(
                                "Clear tags",
                                onClick = model::clearTags,
                                kind = ButtonKind.Secondary,
                                small = true,
                            )
                        }
                    }
                    items(available, key = { it }) { tag ->
                        val on = tag in active
                        Button(
                            tag,
                            onClick = { model.toggleTag(tag) },
                            modifier = Modifier.semantics { selected = on },
                            kind = if (on) ButtonKind.Primary else ButtonKind.Secondary,
                            small = true,
                        )
                    }
                }
            }
        }
        when {
            list is RecipeListState.Failed ->
                item(key = "failed") {
                    ErrorState(
                        (list as RecipeListState.Failed).message,
                        onRetry = model::refresh,
                        modifier = gutter,
                    )
                }
            shown == null ->
                item(key = "loading") {
                    Text("Loading…", gutter, color = MimosTheme.colors.muted)
                }
            shown.isEmpty() ->
                item(key = "empty") {
                    EmptyRecipes(
                        tab = tab,
                        query = query.trim(),
                        tagsActive = active.isNotEmpty(),
                        modifier = gutter,
                        onLibrary = { model.selectTab(RecipeTab.Library) },
                        onEditTab = model::editTab,
                    )
                }
            else ->
                items(shown, key = { it.id }) { recipe ->
                    RecipeRow(recipe, onClick = { model.open(RecipeId(recipe.id)) })
                }
        }
    }
    if (sortOpen) {
        BottomSheet(onDismiss = { sortOpen = false }) {
            for (option in RecipeSort.entries) {
                ListRow(
                    option.label,
                    trailing = if (option == sort) "Selected" else null,
                    onClick = {
                        model.chooseSort(option)
                        sortOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyRecipes(
    tab: RecipeTab,
    query: String,
    tagsActive: Boolean,
    onLibrary: () -> Unit,
    onEditTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        tagsActive -> EmptyState("No matches", "No recipes have all of these tags.", modifier)
        tab is RecipeTab.Collection && query.isNotEmpty() ->
            EmptyState("No matches", "Nothing in this tab matches “$query”.", modifier)
        tab is RecipeTab.Collection ->
            EmptyState(
                "Nothing in this tab yet",
                "Pick recipes for it, or tags whose recipes it gathers.",
                modifier,
                action = { Button("Edit tab", onEditTab, kind = ButtonKind.Secondary) },
            )
        tab == RecipeTab.Mine ->
            EmptyState(
                "No recipes yet",
                "No personal recipes yet. Write your first one, or cook from the library.",
                modifier,
                action = {
                    Button("Browse the library", onLibrary, kind = ButtonKind.Secondary)
                },
            )
        query.isNotEmpty() ->
            EmptyState("No matches", "Nothing in the library matches “$query”.", modifier)
        else -> EmptyState("Nothing here", "The library is empty.", modifier)
    }
}

private fun tabLabel(tab: RecipeTab, loaded: RecipeListState.Loaded?): String =
    when (tab) {
        RecipeTab.Gallery -> "Tabs"
        RecipeTab.Mine -> "My recipes (${loaded?.mine?.size?.toString() ?: "…"})"
        RecipeTab.Library -> "Library (${loaded?.library?.size?.toString() ?: "…"})"
        is RecipeTab.Collection -> loaded?.collection(tab.id)?.name.orEmpty()
    }

/** A recipe as a row: title, description and tags, and servings and calories. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeRow(recipe: RecipeSummary, onClick: () -> Unit) {
    val colors = MimosTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .background(if (pressed) colors.hover else Color.Transparent)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = MimosTheme.spacing.page, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
        ) {
            Text(recipe.title, style = MimosTheme.typography.heading)
            RatedStars(recipe.rating)
            if (recipe.description.isNotEmpty()) {
                Text(
                    recipe.description,
                    style = MimosTheme.typography.small,
                    color = colors.muted,
                    maxLines = 2,
                )
            }
            if (recipe.tags.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(vertical = MimosTheme.spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
                ) {
                    recipe.tags.forEach { Tag(it) }
                }
            }
            Text(
                formatServings(recipe.servings) +
                    (recipe.nutrition.calories?.let { " · ${figure(it, "kcal")}" } ?: ""),
                style = MimosTheme.typography.small,
                color = colors.muted,
            )
        }
        Rule()
    }
}
