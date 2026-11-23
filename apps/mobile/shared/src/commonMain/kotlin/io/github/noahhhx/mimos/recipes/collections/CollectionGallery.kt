package io.github.noahhhx.mimos.recipes.collections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import io.github.noahhhx.mimos.api.models.RecipeCollection
import io.github.noahhhx.mimos.recipes.DefaultState
import io.github.noahhhx.mimos.recipes.RecipeTab
import io.github.noahhhx.mimos.recipes.RecipesModel
import io.github.noahhhx.mimos.recipes.pictures.rememberSavedImage
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.Text

private const val TILES_PER_ROW = 2
private const val TILE_RATIO = 4f / 3f

/** What a tile says under its name: "3 recipes · Default". */
fun tileCaption(collection: RecipeCollection): String =
    (if (collection.recipeCount == 1) "1 recipe" else "${collection.recipeCount} recipes") +
        if (collection.isDefault) " · Default" else ""

/**
 * The gallery (ADR-0036): a tile for each of the household's tabs, its cover filling a 4:3 frame,
 * then a last tile for a new one. Two to a row on a phone.
 */
fun LazyListScope.collectionGallery(
    collections: List<RecipeCollection>,
    model: RecipesModel,
    modifier: Modifier,
) {
    val tiles: List<RecipeCollection?> = collections + null
    tiles.chunked(TILES_PER_ROW).forEachIndexed { row, chunk ->
        item(key = "gallery-$row") {
            Row(
                modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
            ) {
                for (collection in chunk) {
                    val tile = Modifier.weight(1f)
                    if (collection == null) NewTile(model::newTab, tile)
                    else
                        CollectionTile(
                            collection,
                            model,
                            { model.selectTab(RecipeTab.Collection(collection.id)) },
                            tile,
                        )
                }
                repeat(TILES_PER_ROW - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CollectionTile(
    collection: RecipeCollection,
    model: RecipesModel,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MimosTheme.colors
    val pictureId = collection.pictureId
    Tile(onClick, modifier) { pressed ->
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(TILE_RATIO)
                .clipToBounds()
                .background(if (pressed) colors.line else colors.hover)
        ) {
            val image = pictureId?.let { rememberSavedImage(it) { model.cover(collection.id, it) } }
            if (image != null) {
                Image(
                    image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (pictureId == null) {
                Text(
                    collection.name,
                    Modifier.padding(MimosTheme.spacing.m),
                    style = MimosTheme.typography.subheading,
                    color = colors.muted,
                    maxLines = 3,
                )
            }
        }
        Text(
            collection.name,
            Modifier.padding(top = MimosTheme.spacing.xs),
            style = MimosTheme.typography.heading,
            maxLines = 1,
        )
        Text(
            tileCaption(collection),
            style = MimosTheme.typography.small,
            color = colors.muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun NewTile(onClick: () -> Unit, modifier: Modifier) {
    val colors = MimosTheme.colors
    Tile(onClick, modifier) { pressed ->
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(TILE_RATIO)
                .background(if (pressed) colors.hover else colors.surface)
                .border(MimosTheme.spacing.hairline, if (pressed) colors.accent else colors.line),
            contentAlignment = Alignment.Center,
        ) {
            Text("+ New tab", style = MimosTheme.typography.button, color = colors.accent)
        }
    }
}

/** A tile pressed fills deeper, as a row does; nothing animates. */
@Composable
private fun Tile(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (pressed: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        modifier.clickable(
            interactionSource = source,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        ),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs),
    ) {
        content(pressed)
    }
}

/** A tab's own actions above its list: edit it, and open Recipes on it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionActions(
    collection: RecipeCollection,
    defaulting: DefaultState,
    model: RecipesModel,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
        ) {
            Button("Edit tab", model::editTab, kind = ButtonKind.Secondary, small = true)
            Button(
                "Open by default",
                { model.toggleDefault(collection.id) },
                kind = if (collection.isDefault) ButtonKind.Primary else ButtonKind.Secondary,
                small = true,
                enabled = defaulting !is DefaultState.Saving,
                pressed = collection.isDefault,
            )
        }
        if (defaulting is DefaultState.Failed) Notice(defaulting.message)
    }
}
