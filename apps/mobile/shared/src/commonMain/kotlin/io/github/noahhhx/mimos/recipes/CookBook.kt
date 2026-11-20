package io.github.noahhhx.mimos.recipes

import kotlin.jvm.JvmInline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@JvmInline value class RecipeId(val value: String)

/**
 * What is ticked off while cooking one recipe: the positions of its ingredients and steps, and its
 * title, so a trail of followed recipes can name each one.
 */
data class CookState(
    val title: String = "",
    val ingredients: Set<Int> = emptySet(),
    val steps: Set<Int> = emptySet(),
) {
    /** This state without positions the recipe no longer has, as a recipe edited elsewhere may. */
    fun within(ingredientCount: Int, stepCount: Int) =
        copy(
            ingredients = ingredients.filterTo(LinkedHashSet()) { it in 0 until ingredientCount },
            steps = steps.filterTo(LinkedHashSet()) { it in 0 until stepCount },
        )
}

/**
 * The ticks of every recipe cooked since the app opened (ADR-0018, as the web keeps them for a
 * browser tab). Nothing is saved: they last as long as the app's process, and signing out clears
 * them.
 */
class CookBook {
    private val mutableStates = MutableStateFlow<Map<RecipeId, CookState>>(emptyMap())
    val states: StateFlow<Map<RecipeId, CookState>> = mutableStates.asStateFlow()

    /** Notes the recipe's [title], keeping any ticks it already has. */
    fun visit(id: RecipeId, title: String) = change(id) { it.copy(title = title) }

    fun toggleIngredient(id: RecipeId, index: Int) =
        change(id) { it.copy(ingredients = it.ingredients.toggled(index)) }

    fun toggleStep(id: RecipeId, index: Int) =
        change(id) { it.copy(steps = it.steps.toggled(index)) }

    /** The recipe's ticks for a recipe of the given size. */
    fun read(id: RecipeId, ingredientCount: Int, stepCount: Int): CookState =
        (states.value[id] ?: CookState()).within(ingredientCount, stepCount)

    /** The title noted for [id], if the recipe was ever opened. */
    fun title(id: RecipeId): String? = states.value[id]?.title?.takeIf { it.isNotEmpty() }

    fun forget(id: RecipeId) = mutableStates.update { it - id }

    fun clear() {
        mutableStates.value = emptyMap()
    }

    private fun change(id: RecipeId, edit: (CookState) -> CookState) = mutableStates.update {
        it + (id to edit(it[id] ?: CookState()))
    }
}

private fun Set<Int>.toggled(index: Int): Set<Int> =
    if (index in this) this - index else this + index
