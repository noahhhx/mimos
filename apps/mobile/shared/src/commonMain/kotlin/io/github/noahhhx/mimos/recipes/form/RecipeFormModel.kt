package io.github.noahhhx.mimos.recipes.form

import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.IngredientLineStatus
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionSource
import io.github.noahhhx.mimos.api.models.RecipeDetail
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.ingredients.CheckedIngredient
import io.github.noahhhx.mimos.ingredients.NewIngredientDraft
import io.github.noahhhx.mimos.ingredients.SavedIngredient
import io.github.noahhhx.mimos.ingredients.checkInput
import io.github.noahhhx.mimos.ingredients.saveIngredient
import io.github.noahhhx.mimos.ingredients.withIngredient
import io.github.noahhhx.mimos.recipes.pictures.MAX_PICTURES
import io.github.noahhhx.mimos.recipes.pictures.Pick
import io.github.noahhhx.mimos.recipes.pictures.PickedPicture
import io.github.noahhhx.mimos.recipes.pictures.PictureApi
import io.github.noahhhx.mimos.recipes.pictures.PictureDraft
import io.github.noahhhx.mimos.recipes.pictures.PictureShrinker
import io.github.noahhhx.mimos.recipes.pictures.Shrunk
import io.github.noahhhx.mimos.recipes.pictures.syncPictures
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

/** The new ingredient being added from ingredient row [row] (ADR-0016). */
data class RowIngredient(val row: Int, val draft: NewIngredientDraft)

sealed interface RecipeFormState {
    /** No form is open. */
    data object Closed : RecipeFormState

    /** Loading what the form searches (and the recipe, for an edit). */
    data object Loading : RecipeFormState

    data class Failed(val message: String) : RecipeFormState

    /**
     * The form. [recipeId] is the recipe being edited, null for a new one until its first save.
     * [recipes] are the household's own, never the recipe itself. [errors] are the last save's,
     * shown at the top. [pictures] are kept apart from [draft] because they are saved apart from it
     * (ADR-0023); [pictureProblems] are the last pick's, said beside them.
     */
    data class Editing(
        val recipeId: String?,
        val draft: RecipeDraft,
        val catalog: List<CatalogIngredient>,
        val recipes: List<RecipeSummary>,
        val pictures: PictureDraft = PictureDraft(),
        val estimate: Estimate = Estimate.None,
        val adding: RowIngredient? = null,
        val errors: List<String> = emptyList(),
        val saving: Boolean = false,
        val shrinking: Boolean = false,
        val pictureProblems: List<String> = emptyList(),
    ) : RecipeFormState {
        val bySlug: Map<String, CatalogIngredient> = catalog.associateBy { it.slug }
        val byRecipeId: Map<String, RecipeSummary> = recipes.associateBy { it.id }
    }
}

/**
 * The recipe form, for a new recipe or an edit, as the web's `recipe-form.tsx`: ingredient lines
 * linked to the catalog or the household's recipes as they are typed and picked, a new ingredient
 * added from a line, and the nutrition estimate kept up to date [estimateDelayMillis] after the
 * last change to what it is sent. Save sends the fields, then the picture changes through
 * [pictureApi]; if a picture fails, the form stays open with the rest still to send, now editing
 * the recipe it saved, so saving again never creates it twice. [onSaved] gets the saved recipe.
 * [api] is asked for on every call, because the instance can change.
 */
class RecipeFormModel(
    private val scope: CoroutineScope,
    private val api: () -> RecipesApi,
    private val pictureApi: PictureApi,
    private val shrinker: PictureShrinker,
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
    private var shrinkJob: Job? = null
    private var requested: EstimateRequest? = null
    private var picks = 0

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
                        val recipe = recipeId?.let { async { api().getRecipe(it).body() } }?.await()
                        RecipeFormState.Editing(
                            recipeId = recipeId,
                            draft = draftOf(recipe),
                            pictures = PictureDraft.of(recipe?.pictures.orEmpty().map { it.id }),
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
        for (job in listOf(loadJob, saveJob, ingredientJob, estimateJob, shrinkJob)) job?.cancel()
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
        form.copy(
            adding =
                RowIngredient(index, NewIngredientDraft(row.name.trim(), basisForUnit(row.unit)))
        )
    }

    fun editNewIngredient(change: (NewIngredientDraft) -> NewIngredientDraft) =
        changeEditing { form ->
            form.copy(adding = form.adding?.let { it.copy(draft = change(it.draft)) })
        }

    fun cancelNewIngredient() = changeEditing { it.copy(adding = null) }

    /**
     * Saves the new ingredient as one of the household's own, adds it to what the form searches,
     * and links its row to it. Its row may have moved meanwhile; if the row was removed, the
     * ingredient is still saved and searchable.
     */
    fun saveNewIngredient() {
        val form = mutableState.value as? RecipeFormState.Editing ?: return
        val adding = form.adding?.draft?.takeIf { !it.saving } ?: return
        val input =
            when (val checked = adding.checkInput()) {
                is CheckedIngredient.Invalid -> {
                    editNewIngredient { it.copy(error = checked.message) }
                    return
                }
                is CheckedIngredient.Valid -> checked.input
            }
        editNewIngredient { it.copy(saving = true) }
        ingredientJob = scope.launch {
            when (val saved = api().saveIngredient(input)) {
                is SavedIngredient.Failed ->
                    editNewIngredient { it.copy(saving = false, error = saved.message) }
                is SavedIngredient.Created ->
                    changeEditing { current ->
                        val open = current.adding
                        val catalog = withIngredient(current.catalog, saved.ingredient)
                        if (open == null) current.copy(catalog = catalog)
                        else
                            current.copy(catalog = catalog, adding = null).withRow(open.row) {
                                pickEntry(it, saved.ingredient)
                            }
                    }
            }
        }
    }

    /**
     * Shrinks each of [picked] in turn and adds those that could be after the form's pictures, up
     * to [MAX_PICTURES]. Nothing reaches the API until the form is saved.
     */
    fun addPictures(picked: List<PickedPicture>) {
        val form = mutableState.value as? RecipeFormState.Editing ?: return
        if (form.shrinking || form.saving || picked.isEmpty()) return
        changeEditing { it.copy(shrinking = true, pictureProblems = emptyList()) }
        shrinkJob = scope.launch {
            val ready = mutableListOf<Pick>()
            val problems = mutableListOf<String>()
            for (each in picked) {
                when (val shrunk = shrinker.shrink(each)) {
                    is Shrunk.Ready -> ready += Pick("pick-${++picks}", shrunk.prepared)
                    is Shrunk.Failed -> problems += shrunk.message
                }
            }
            changeEditing { current ->
                val (pictures, leftOut) = current.pictures.add(ready)
                val full =
                    if (leftOut == 0) emptyList()
                    else
                        listOf(
                            "A recipe can have $MAX_PICTURES pictures, so $leftOut of them were " +
                                "left out."
                        )
                current.copy(
                    pictures = pictures,
                    shrinking = false,
                    pictureProblems = problems + full,
                )
            }
        }
    }

    fun removePicture(index: Int) = changeEditing {
        if (it.saving || it.shrinking) it else it.copy(pictures = it.pictures.remove(index))
    }

    /** Saves the recipe, or shows every problem with the form at its top. */
    fun save() {
        val form = mutableState.value as? RecipeFormState.Editing ?: return
        if (form.saving || form.shrinking) return
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
            changeEditing { it.copy(recipeId = saved.id) }
            if (form.pictures.hasChanges) {
                val synced = syncPictures(pictureApi, saved.id, form.pictures)
                changeEditing { it.copy(pictures = synced.draft) }
                if (synced.error != null) {
                    changeEditing { it.copy(saving = false, errors = listOf(synced.error)) }
                    return@launch
                }
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
