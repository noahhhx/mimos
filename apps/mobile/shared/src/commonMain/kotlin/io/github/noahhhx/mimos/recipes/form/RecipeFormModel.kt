package io.github.noahhhx.mimos.recipes.form

import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.IngredientInput
import io.github.noahhhx.mimos.api.models.IngredientLineStatus
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.api.models.NutritionSource
import io.github.noahhhx.mimos.api.models.RecipeDetail
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.recipes.collationKey
import io.github.noahhhx.mimos.recipes.compareKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The calculated nutrition for the form as it stands. */
sealed interface Estimate {
    /** Nothing to calculate: nutrition is typed in, or servings is not a whole number to 50. */
    data object None : Estimate

    /** Waiting for the pause after typing, or for the answer; [last] is shown meanwhile. */
    data class Pending(val last: Ready?) : Estimate

    data object Failed : Estimate

    /** [statuses] is keyed by form row, for each row that was sent. */
    data class Ready(val nutrition: Nutrition, val statuses: Map<Int, IngredientLineStatus>) :
        Estimate
}

/** The new ingredient being added from ingredient row [row] (ADR-0016), as typed. */
data class NewIngredientDraft(
    val row: Int,
    val name: String,
    val basis: NutritionBasis,
    val calories: String = "",
    val proteinG: String = "",
    val carbsG: String = "",
    val fatG: String = "",
    val error: String? = null,
    val saving: Boolean = false,
)

sealed interface RecipeFormState {
    /** No form is open. */
    data object Closed : RecipeFormState

    /** Loading what the form searches (and the recipe, for an edit). */
    data object Loading : RecipeFormState

    data class Failed(val message: String) : RecipeFormState

    /**
     * The form. [recipeId] is the recipe being edited, null for a new one. [recipes] are the
     * household's own, never the recipe itself. [errors] are the last save's, shown at the top.
     */
    data class Editing(
        val recipeId: String?,
        val draft: RecipeDraft,
        val catalog: List<CatalogIngredient>,
        val recipes: List<RecipeSummary>,
        val estimate: Estimate = Estimate.None,
        val adding: NewIngredientDraft? = null,
        val errors: List<String> = emptyList(),
        val saving: Boolean = false,
    ) : RecipeFormState {
        val bySlug: Map<String, CatalogIngredient> = catalog.associateBy { it.slug }
        val byRecipeId: Map<String, RecipeSummary> = recipes.associateBy { it.id }
    }
}

/**
 * The recipe form, for a new recipe or an edit, as the web's `recipe-form.tsx`: ingredient lines
 * linked to the catalog or the household's recipes as they are typed and picked, a new ingredient
 * added from a line, and the nutrition estimate kept up to date [estimateDelayMillis] after the
 * last change to what it is sent. [onSaved] gets the saved recipe. [api] is asked for on every
 * call, because the instance can change.
 */
class RecipeFormModel(
    private val scope: CoroutineScope,
    private val api: () -> RecipesApi,
    private val onSaved: (RecipeDetail) -> Unit,
    private val estimateDelayMillis: Long = ESTIMATE_DELAY_MILLIS,
) {
    private val mutableState = MutableStateFlow<RecipeFormState>(RecipeFormState.Closed)
    val state: StateFlow<RecipeFormState> = mutableState.asStateFlow()

    private var openedFor: String? = null
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var ingredientJob: Job? = null
    private var estimateJob: Job? = null
    private var requested: EstimateRequest? = null

    /** Opens the form for the recipe [recipeId], or for a new recipe. */
    fun open(recipeId: String?) {
        close()
        openedFor = recipeId
        mutableState.value = RecipeFormState.Loading
        loadJob = scope.launch {
            val loaded =
                try {
                    coroutineScope {
                        val catalog = async { api().listIngredients().body() }
                        val mine = async { api().listMyRecipes().body() }
                        val recipe = recipeId?.let { async { api().getRecipe(it).body() } }
                        RecipeFormState.Editing(
                            recipeId = recipeId,
                            draft = draftOf(recipe?.await()),
                            catalog = catalog.await(),
                            // A recipe never offers itself as something it can use.
                            recipes = mine.await().filter { it.id != recipeId },
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    RecipeFormState.Failed(describe(failure, "The API rejected the request."))
                }
            mutableState.value = loaded
            estimateIfChanged()
        }
    }

    fun retry() = open(openedFor)

    /** Closes the form, dropping what was typed and anything in flight. */
    fun close() {
        for (job in listOf(loadJob, saveJob, ingredientJob, estimateJob)) job?.cancel()
        requested = null
        mutableState.value = RecipeFormState.Closed
    }

    /** Any change to the recipe's own fields: title, times, nutrition, and the like. */
    fun edit(change: (RecipeDraft) -> RecipeDraft) = changeDraft(change)

    fun editRow(index: Int, change: (IngredientRow) -> IngredientRow) = changeRow(index, change)

    /** Types a row's name, which may link it, keep its link, or drop it ([retype]). */
    fun typeName(index: Int, name: String) = changeEditing { form ->
        form.withRow(index) { retype(it, name, form.catalog, form.bySlug, form.byRecipeId) }
    }

    fun pick(index: Int, entry: CatalogIngredient) = changeRow(index) { pickEntry(it, entry) }

    fun pick(index: Int, recipe: RecipeSummary) = changeRow(index) { pickRecipe(it, recipe) }

    fun dontCount(index: Int) = changeRow(index, ::dontCount)

    fun addIngredient() = changeDraft { it.copy(ingredients = it.ingredients + IngredientRow()) }

    fun removeIngredient(index: Int) = changeEditing { form ->
        val adding = form.adding
        form.copy(
            draft =
                form.draft.copy(
                    ingredients = form.draft.ingredients.filterIndexed { i, _ -> i != index }
                ),
            adding =
                when {
                    adding == null || adding.row < index -> adding
                    adding.row == index -> null
                    else -> adding.copy(row = adding.row - 1)
                },
        )
    }

    /** Moves a row one place, and the new ingredient open on it, if any, with it. */
    fun moveIngredient(from: Int, to: Int) = changeEditing { form ->
        val rows = form.draft.ingredients
        if (from !in rows.indices || to !in rows.indices) return@changeEditing form
        val moved = rows.toMutableList().apply { add(to, removeAt(from)) }
        val adding = form.adding
        form.copy(
            draft = form.draft.copy(ingredients = moved),
            adding =
                when (adding?.row) {
                    from -> adding.copy(row = to)
                    to -> adding.copy(row = from)
                    else -> adding
                },
        )
    }

    fun addStep() = changeDraft { it.copy(steps = it.steps + "") }

    fun editStep(index: Int, text: String) = changeDraft { draft ->
        draft.copy(steps = draft.steps.mapIndexed { i, step -> if (i == index) text else step })
    }

    fun removeStep(index: Int) = changeDraft { draft ->
        draft.copy(steps = draft.steps.filterIndexed { i, _ -> i != index })
    }

    /** Opens the new ingredient on row [index], named as the row is, its basis from the unit. */
    fun startNewIngredient(index: Int) = changeEditing { form ->
        val row = form.draft.ingredients.getOrNull(index) ?: return@changeEditing form
        form.copy(adding = NewIngredientDraft(index, row.name.trim(), basisForUnit(row.unit)))
    }

    fun editNewIngredient(change: (NewIngredientDraft) -> NewIngredientDraft) =
        changeEditing { form ->
            form.copy(adding = form.adding?.let(change))
        }

    fun cancelNewIngredient() = changeEditing { it.copy(adding = null) }

    /**
     * Saves the new ingredient as one of the household's own, adds it to what the form searches,
     * and links its row to it. Its row may have moved meanwhile; if the row was removed, the
     * ingredient is still saved and searchable.
     */
    fun saveNewIngredient() {
        val form = mutableState.value as? RecipeFormState.Editing ?: return
        val adding = form.adding?.takeIf { !it.saving } ?: return
        val values = listOf(adding.calories, adding.proteinG, adding.carbsG, adding.fatG)
        val numbers = values.map(::jsNumber)
        val error =
            when {
                adding.name.isBlank() -> "Give the ingredient a name."
                values.any { it.isBlank() } || numbers.any { !(it >= 0) } ->
                    "Fill in all four values, using 0 when there is none."
                else -> null
            }
        if (error != null) {
            editNewIngredient { it.copy(error = error) }
            return
        }
        editNewIngredient { it.copy(saving = true) }
        ingredientJob = scope.launch {
            val created =
                try {
                    api()
                        .createIngredient(
                            IngredientInput(
                                name = adding.name.trim(),
                                basis = adding.basis,
                                nutrition =
                                    Nutrition(numbers[0], numbers[1], numbers[2], numbers[3]),
                            )
                        )
                        .body()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val message = describe(failure, "The ingredient could not be saved.")
                    editNewIngredient { it.copy(saving = false, error = message) }
                    return@launch
                }
            changeEditing { current ->
                val open = current.adding
                val catalog =
                    (current.catalog + created).sortedWith { a, b ->
                        compareKeys(collationKey(a.name), collationKey(b.name))
                    }
                if (open == null) current.copy(catalog = catalog)
                else
                    current.copy(catalog = catalog, adding = null).withRow(open.row) {
                        pickEntry(it, created)
                    }
            }
        }
    }

    /** Saves the recipe, or shows every problem with the form at its top. */
    fun save() {
        val form = mutableState.value as? RecipeFormState.Editing ?: return
        if (form.saving) return
        val input =
            when (val checked = form.draft.toRecipeInput()) {
                is CheckedRecipe.Invalid -> {
                    changeEditing { it.copy(errors = checked.errors) }
                    return
                }
                is CheckedRecipe.Valid -> checked.input
            }
        changeEditing { it.copy(saving = true, errors = emptyList()) }
        saveJob = scope.launch {
            val saved =
                try {
                    val id = form.recipeId
                    if (id != null) api().replaceRecipe(id, input).body()
                    else api().createRecipe(input).body()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val message = describe(failure, "The API rejected the recipe.")
                    changeEditing { it.copy(saving = false, errors = listOf(message)) }
                    return@launch
                }
            onSaved(saved)
        }
    }

    private fun changeDraft(change: (RecipeDraft) -> RecipeDraft) = changeEditing {
        it.copy(draft = change(it.draft))
    }

    private fun changeRow(index: Int, change: (IngredientRow) -> IngredientRow) = changeEditing {
        it.withRow(index, change)
    }

    private fun changeEditing(change: (RecipeFormState.Editing) -> RecipeFormState.Editing) {
        mutableState.update { if (it is RecipeFormState.Editing) change(it) else it }
        estimateIfChanged()
    }

    /**
     * Asks for the estimate again when what it would be sent changes, rows included, so the
     * statuses always land on the rows they are for. Typing anywhere else asks nothing.
     */
    private fun estimateIfChanged() {
        val form = mutableState.value as? RecipeFormState.Editing ?: return
        val request =
            if (form.draft.nutritionSource == NutritionSource.INGREDIENTS) {
                form.draft.toEstimateInput()
            } else null
        if (request == requested) return
        requested = request
        estimateJob?.cancel()
        if (request == null) {
            setEstimate(Estimate.None)
            return
        }
        val last =
            when (val shown = form.estimate) {
                is Estimate.Ready -> shown
                is Estimate.Pending -> shown.last
                else -> null
            }
        setEstimate(Estimate.Pending(last))
        estimateJob = scope.launch {
            delay(estimateDelayMillis)
            val estimate =
                try {
                    val answer = api().estimateRecipeNutrition(request.input).body()
                    Estimate.Ready(answer.nutrition, request.rows.zip(answer.lines).toMap())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Estimate.Failed
                }
            setEstimate(estimate)
        }
    }

    private fun setEstimate(estimate: Estimate) = mutableState.update {
        if (it is RecipeFormState.Editing) it.copy(estimate = estimate) else it
    }

    private fun describe(failure: Exception, rejected: String): String =
        if (failure is ApiProblem) failure.detail ?: rejected else "Could not reach the server."

    companion object {
        const val ESTIMATE_DELAY_MILLIS = 300L
    }
}

private fun RecipeFormState.Editing.withRow(
    index: Int,
    change: (IngredientRow) -> IngredientRow,
): RecipeFormState.Editing =
    copy(
        draft =
            draft.copy(
                ingredients =
                    draft.ingredients.mapIndexed { i, row -> if (i == index) change(row) else row }
            )
    )
