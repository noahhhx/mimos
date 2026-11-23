package io.github.noahhhx.mimos.recipes.collections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.recipes.RecipesModel
import io.github.noahhhx.mimos.recipes.pictures.rememberNewImage
import io.github.noahhhx.mimos.recipes.pictures.rememberPicturePicker
import io.github.noahhhx.mimos.recipes.pictures.rememberSavedImage
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.MimosDialog
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField
import io.github.noahhhx.mimos.ui.TickBox

@Composable
fun CollectionFormScreen(model: RecipesModel, title: String, onCancel: () -> Unit) {
    val state by model.tabForm.state.collectAsState()
    val scroll = rememberScrollState()
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = MimosTheme.spacing.xxl)
    ) {
        Text(
            "← Recipes",
            gutter
                .clickable(role = Role.Button, onClick = onCancel)
                .padding(vertical = MimosTheme.spacing.s),
            color = MimosTheme.colors.muted,
        )
        PageHeader(title, gutter.padding(top = MimosTheme.spacing.l))
        when (val current = state) {
            CollectionFormState.Closed,
            CollectionFormState.Loading -> Text("Loading…", gutter, color = MimosTheme.colors.muted)
            is CollectionFormState.Failed ->
                ErrorState(current.message, onRetry = model.tabForm::retry, modifier = gutter)
            is CollectionFormState.Editing -> {
                LaunchedEffect(current.error) { if (current.error != null) scroll.scrollTo(0) }
                Form(model, current, onCancel, gutter)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    model: RecipesModel,
    form: CollectionFormState.Editing,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val tabs = model.tabForm
    val draft = form.draft
    val spacing = MimosTheme.spacing
    val colors = MimosTheme.colors
    var typedTag by remember { mutableStateOf("") }
    var find by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val tags = remember(form.recipes, draft.tags) { tagChoices(form.recipes, draft.tags) }
    val found = remember(form.recipes, find) { findRecipes(form.recipes, find) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        form.error?.let { Notice(it) }

        TextField(
            draft.name,
            { v -> tabs.edit { it.copy(name = v) } },
            "Name",
            maxLength = MAX_NAME_LENGTH,
        )

        Label("Cover")
        CoverField(model, form)

        Label("Tags")
        Text(
            "Every recipe with any of these tags is in the tab, as it gets them.",
            style = MimosTheme.typography.small,
            color = colors.muted,
        )
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                for (tag in tags) {
                    val on = tag in draft.tags
                    Button(
                        tag,
                        { tabs.edit { it.toggleTag(tag) } },
                        kind = if (on) ButtonKind.Primary else ButtonKind.Secondary,
                        small = true,
                        enabled = on || !draft.tagsFull,
                        pressed = on,
                    )
                }
            }
        }
        val addTag = {
            tabs.edit { it.addTag(typedTag) }
            typedTag = ""
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                typedTag,
                { typedTag = it },
                "New tag",
                Modifier.weight(1f),
                maxLength = MAX_TAG_LENGTH,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTag() }),
            )
            Button(
                "Add tag",
                addTag,
                kind = ButtonKind.Secondary,
                enabled = typedTag.isNotBlank() && !draft.tagsFull,
            )
        }
        if (draft.tagsFull) {
            Text(
                "A tab can have $MAX_TAGS tags.",
                style = MimosTheme.typography.small,
                color = colors.muted,
            )
        }

        Label("Recipes")
        Text(
            when (draft.recipeIds.size) {
                0 -> "Pick recipes to keep in the tab, whatever their tags."
                1 -> "1 recipe picked."
                else -> "${draft.recipeIds.size} recipes picked."
            },
            style = MimosTheme.typography.small,
            color = colors.muted,
        )
        TextField(find, { find = it }, "Find a recipe")
        Column {
            for (recipe in found) {
                CheckRow(
                    recipe.title,
                    detail = if (recipe.isLibrary) "Library" else null,
                    checked = recipe.id in draft.recipeIds,
                    onToggle = { tabs.edit { it.toggleRecipe(recipe.id) } },
                )
            }
            if (found.isEmpty()) {
                Text(
                    if (form.recipes.isEmpty()) "No recipes to pick yet."
                    else "No recipe is called that.",
                    color = colors.muted,
                )
            }
        }

        CheckRow(
            "Open Recipes on this tab",
            detail = "Only for you. Others in the household choose their own.",
            checked = draft.openByDefault,
            onToggle = { tabs.edit { it.copy(openByDefault = !it.openByDefault) } },
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Button(
                when {
                    form.saving -> "Saving…"
                    form.saved != null -> "Save changes"
                    else -> "Create tab"
                },
                tabs::save,
                enabled = !form.busy,
            )
            Button("Cancel", onCancel, kind = ButtonKind.Secondary, enabled = !form.busy)
            if (form.saved != null) {
                Button(
                    if (form.deleting) "Deleting…" else "Delete tab",
                    { confirmDelete = true },
                    kind = ButtonKind.Danger,
                    enabled = !form.busy,
                )
            }
        }
    }

    if (confirmDelete) {
        MimosDialog(
            title = "Delete “${form.saved?.name ?: draft.name}”?",
            onDismiss = { confirmDelete = false },
            actions = {
                Button("Cancel", onClick = { confirmDelete = false }, kind = ButtonKind.Secondary)
                Button(
                    "Delete",
                    onClick = {
                        confirmDelete = false
                        tabs.delete()
                    },
                    kind = ButtonKind.Danger,
                )
            },
        ) {
            Text("Its recipes stay where they are. This cannot be undone.", color = colors.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverField(model: RecipesModel, form: CollectionFormState.Editing) {
    val tabs = model.tabForm
    val spacing = MimosTheme.spacing
    val picker = rememberPicturePicker(tabs::pickCover, single = true)
    val cover = form.draft.cover
    val saved = form.saved
    val image: ImageBitmap? =
        when (cover) {
            Cover.None -> null
            is Cover.New -> rememberNewImage(cover.prepared)
            is Cover.Saved ->
                saved?.let {
                    rememberSavedImage(cover.pictureId) { model.cover(it.id, cover.pictureId) }
                }
        }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        if (cover != Cover.None) {
            Box(
                Modifier.fillMaxWidth(0.6f)
                    .aspectRatio(4f / 3f)
                    .clipToBounds()
                    .background(MimosTheme.colors.hover)
                    .semantics { contentDescription = "Cover" }
            ) {
                image?.let {
                    Image(
                        it,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (cover is Cover.New) {
                Text(
                    "Not saved yet",
                    style = MimosTheme.typography.small,
                    color = MimosTheme.colors.muted,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Button(
                when {
                    form.shrinking -> "Shrinking…"
                    cover == Cover.None -> "Choose a cover"
                    else -> "Replace cover"
                },
                picker.pick,
                kind = ButtonKind.Secondary,
                enabled = !form.busy,
            )
            picker.takePhoto?.let {
                Button("Take a photo", it, kind = ButtonKind.Secondary, enabled = !form.busy)
            }
            if (cover != Cover.None) {
                Button(
                    "Remove cover",
                    tabs::removeCover,
                    kind = ButtonKind.Secondary,
                    enabled = !form.busy,
                )
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, detail: String?, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TickBox(checked)
        Column(Modifier.weight(1f)) {
            Text(label)
            if (detail != null) {
                Text(detail, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
            }
        }
    }
}
