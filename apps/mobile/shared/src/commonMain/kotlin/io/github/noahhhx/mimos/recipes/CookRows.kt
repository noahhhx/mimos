package io.github.noahhhx.mimos.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.api.models.IngredientQuantity
import io.github.noahhhx.mimos.api.models.RecipeStep
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TickBox

private val AMOUNT_WIDTH = 64.dp

/** Figures line up down the amount column. */
private const val TABULAR = "tnum"

/**
 * One ingredient, ticked by tapping anywhere on its row: its amount in a column, its name beside
 * it. A line that is one of the household's recipes is a link inside the row: tapping its name
 * calls [onFollow] instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IngredientRow(
    ingredient: IngredientQuantity,
    checked: Boolean,
    onToggle: () -> Unit,
    onFollow: (RecipeId) -> Unit,
) {
    val colors = MimosTheme.colors
    val struck =
        MimosTheme.typography.tickRow.copy(
            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
        )
    val color = if (checked) colors.muted else colors.fg
    val note = ingredient.note?.takeIf { it.isNotEmpty() }?.let { ", $it" } ?: ""
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = MimosTheme.spacing.page, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        TickBox(checked, Modifier.padding(top = 2.dp), side = 20.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
            Text(
                formatQuantity(ingredient.quantity, ingredient.unit),
                Modifier.width(AMOUNT_WIDTH),
                style = struck.copy(fontFeatureSettings = TABULAR),
                color = colors.muted,
            )
            val linked = ingredient.recipeId
            if (linked == null) {
                Text(
                    countedName(ingredient.name, ingredient.quantity, ingredient.unit) + note,
                    style = struck,
                    color = color,
                )
            } else {
                FlowRow {
                    Text(
                        ingredient.name,
                        Modifier.clickable(role = Role.Button) { onFollow(RecipeId(linked)) },
                        style = struck.copy(textDecoration = TextDecoration.Underline),
                        color = colors.fg,
                    )
                    if (note.isNotEmpty()) Text(note, style = struck, color = color)
                }
            }
        }
    }
}

/** One step of the method, ticked by tapping its row; a done step is muted and struck through. */
@Composable
fun StepRow(number: Int, step: RecipeStep, checked: Boolean, onToggle: () -> Unit) {
    val colors = MimosTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = MimosTheme.spacing.page, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
    ) {
        Text("$number.", Modifier.width(25.dp), color = colors.muted)
        Text(
            step.instruction,
            style =
                MimosTheme.typography.body.copy(
                    textDecoration =
                        if (checked) TextDecoration.LineThrough else TextDecoration.None
                ),
            color = if (checked) colors.muted else colors.fg,
        )
    }
}
