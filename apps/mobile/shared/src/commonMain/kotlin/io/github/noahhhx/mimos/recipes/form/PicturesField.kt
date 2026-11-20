package io.github.noahhhx.mimos.recipes.form

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.noahhhx.mimos.recipes.pictures.MAX_PICTURES
import io.github.noahhhx.mimos.recipes.pictures.NewPicture
import io.github.noahhhx.mimos.recipes.pictures.PICTURE_HEIGHT_COMPACT
import io.github.noahhhx.mimos.recipes.pictures.PictureSlot
import io.github.noahhhx.mimos.recipes.pictures.RecipePictures
import io.github.noahhhx.mimos.recipes.pictures.SavedPicture
import io.github.noahhhx.mimos.recipes.pictures.pictureDescription
import io.github.noahhhx.mimos.recipes.pictures.rememberPicturePicker
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.Text

/**
 * The recipe form's pictures, as the web's `pictures-field.tsx`: saved pictures and new picks in
 * one row, each with Remove. A pick is shrunk as soon as it is chosen, so a file that is not a
 * picture says so at once. Nothing reaches the API until the form is saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PicturesField(
    model: RecipeFormModel,
    form: RecipeFormState.Editing,
    pictures: RecipePictures,
) {
    val colors = MimosTheme.colors
    val spacing = MimosTheme.spacing
    val draft = form.pictures
    val busy = form.saving || form.shrinking
    val picker = rememberPicturePicker(model::addPictures)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            "Up to $MAX_PICTURES, shown above the method in this order. They are shrunk before " +
                "they are sent, and saved with the recipe.",
            style = MimosTheme.typography.small,
            color = colors.muted,
        )
        if (draft.slots.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val widest = maxWidth * 0.85f
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                ) {
                    draft.slots.forEachIndexed { index, slot ->
                        val description =
                            pictureDescription(form.draft.title, index, draft.slots.size)
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                            when (slot) {
                                is PictureSlot.New ->
                                    NewPicture(
                                        slot.prepared,
                                        description,
                                        PICTURE_HEIGHT_COMPACT,
                                        widest,
                                    )
                                is PictureSlot.Saved ->
                                    form.recipeId?.let { recipeId ->
                                        SavedPicture(
                                            pictures,
                                            recipeId,
                                            slot.id,
                                            description,
                                            PICTURE_HEIGHT_COMPACT,
                                            widest,
                                        )
                                    }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (slot is PictureSlot.New) {
                                    Text(
                                        "Not saved yet",
                                        style = MimosTheme.typography.small,
                                        color = colors.muted,
                                    )
                                }
                                Button(
                                    "Remove",
                                    { model.removePicture(index) },
                                    kind = ButtonKind.Secondary,
                                    small = true,
                                    enabled = !busy,
                                    description = "Remove picture ${index + 1}",
                                )
                            }
                        }
                    }
                }
            }
        }
        if (form.pictureProblems.isNotEmpty()) Notice(form.pictureProblems.joinToString("\n"))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                if (form.shrinking) "Shrinking…" else "Add pictures",
                picker.pick,
                kind = ButtonKind.Secondary,
                enabled = !busy && !draft.full,
            )
            picker.takePhoto?.let {
                Button(
                    "Take a photo",
                    it,
                    kind = ButtonKind.Secondary,
                    enabled = !busy && !draft.full,
                )
            }
            if (draft.full) {
                Text(
                    "A recipe can have $MAX_PICTURES pictures.",
                    style = MimosTheme.typography.small,
                    color = colors.muted,
                )
            }
        }
    }
}
