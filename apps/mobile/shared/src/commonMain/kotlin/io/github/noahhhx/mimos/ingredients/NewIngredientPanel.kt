package io.github.noahhhx.mimos.ingredients

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.SelectField
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField

private val DECIMAL = KeyboardOptions(keyboardType = KeyboardType.Decimal)

private val BASES =
    listOf(NutritionBasis.PER_100_G, NutritionBasis.PER_100_ML, NutritionBasis.PER_PIECE)

private fun basisLabel(basis: NutritionBasis): String =
    when (basis) {
        NutritionBasis.PER_100_G -> "100 g"
        NutritionBasis.PER_100_ML -> "100 ml"
        NutritionBasis.PER_PIECE -> "One piece"
        NutritionBasis.unknown_default_open_api -> "Other"
    }

/**
 * Adds an ingredient of the household's own (ADR-0016) from wherever one is missing: the recipe
 * form's ingredient line, or the plan's picker. [saveLabel] names what saving does there.
 */
@Composable
fun NewIngredientPanel(
    draft: NewIngredientDraft,
    onEdit: ((NewIngredientDraft) -> NewIngredientDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    saveLabel: String = "Save ingredient",
    enabled: Boolean = true,
) {
    val spacing = MimosTheme.spacing
    val colors = MimosTheme.colors
    Column(
        modifier.fillMaxWidth().border(spacing.hairline, colors.line).padding(spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Label("New ingredient")
        Text(
            "A new ingredient of your own. Copy its nutrition from the label; only you will see it.",
            style = MimosTheme.typography.small,
            color = colors.muted,
        )
        draft.error?.let { Text(it, style = MimosTheme.typography.small, color = colors.danger) }
        TextField(
            draft.name,
            { v -> onEdit { it.copy(name = v) } },
            "Name",
            description = "New ingredient name",
            maxLength = 100,
        )
        SelectField(
            "Nutrition per",
            selected = draft.basis,
            options = BASES,
            optionLabel = ::basisLabel,
            onSelect = { v -> onEdit { it.copy(basis = v) } },
            description = "New ingredient nutrition per",
        )
        NutritionFields(
            listOf(draft.calories, draft.proteinG, draft.carbsG, draft.fatG),
            labels = listOf("Calories", "Protein g", "Carbs g", "Fat g"),
            descriptions =
                listOf(
                    "New ingredient calories",
                    "New ingredient protein g",
                    "New ingredient carbs g",
                    "New ingredient fat g",
                ),
            onChange = { field, v ->
                onEdit {
                    when (field) {
                        0 -> it.copy(calories = v)
                        1 -> it.copy(proteinG = v)
                        2 -> it.copy(carbsG = v)
                        else -> it.copy(fatG = v)
                    }
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Button(
                if (draft.saving) "Saving…" else saveLabel,
                onSave,
                enabled = enabled && !draft.saving,
            )
            Button(
                "Cancel",
                onCancel,
                kind = ButtonKind.Secondary,
                enabled = !draft.saving,
                description = "Cancel the new ingredient",
            )
        }
    }
}

/** Four nutrition values, two to a row. */
@Composable
fun NutritionFields(
    values: List<String>,
    labels: List<String>,
    descriptions: List<String>,
    onChange: (Int, String) -> Unit,
) {
    val spacing = MimosTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        for (pair in listOf(0, 2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                for (field in pair..pair + 1) {
                    TextField(
                        values[field],
                        { onChange(field, it) },
                        labels[field],
                        Modifier.weight(1f),
                        description = descriptions[field],
                        keyboardOptions = DECIMAL,
                    )
                }
            }
        }
    }
}
