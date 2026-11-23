package io.github.noahhhx.mimos.recipes.collections

import io.github.noahhhx.mimos.api.apis.RecipeCollectionsApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.RecipeCollection
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.recipes.pictures.PickedPicture
import io.github.noahhhx.mimos.recipes.pictures.PictureShrinker
import io.github.noahhhx.mimos.recipes.pictures.Shrunk
import io.github.noahhhx.mimos.recipes.pictures.jpegFilePart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CollectionFormState {
    data object Closed : CollectionFormState

    data object Loading : CollectionFormState

    data class Failed(val message: String) : CollectionFormState

    /**
     * The form. [saved] is the tab as the server last answered it, null for a new tab until its
     * first save. [recipes] are the household's own then the library's, which the form picks from
     * and takes its tag choices from.
     */
    data class Editing(
        val saved: RecipeCollection?,
        val draft: CollectionDraft,
        val recipes: List<RecipeSummary>,
        val error: String? = null,
        val saving: Boolean = false,
        val shrinking: Boolean = false,
        val deleting: Boolean = false,
    ) : CollectionFormState {
        val busy: Boolean
            get() = saving || shrinking || deleting
    }
}

/**
 * The tab form, new or edit (ADR-0036). Save converges as the recipe form does: the fields, then
 * the cover, then the default, each answer kept as [CollectionFormState.Editing.saved] at once, so
 * a failure leaves the form editing the tab it made and saving again never makes it twice.
 * [onSaved] gets the tab, [onDeleted] is told when it is gone.
 */
class CollectionFormModel(
    private val scope: CoroutineScope,
    private val recipes: () -> RecipesApi,
    private val api: () -> RecipeCollectionsApi,
    private val shrinker: PictureShrinker,
    private val onSaved: (RecipeCollection) -> Unit,
    private val onDeleted: () -> Unit,
) {
    private val mutableState = MutableStateFlow<CollectionFormState>(CollectionFormState.Closed)
    val state: StateFlow<CollectionFormState> = mutableState.asStateFlow()

    private var openedFor: String? = null
    private var job: Job? = null

    /** Opens the form for the tab [collectionId], or for a new one. */
    fun open(collectionId: String?) {
        close()
        openedFor = collectionId
        mutableState.value = CollectionFormState.Loading
        job = scope.launch {
            mutableState.value =
                try {
                    coroutineScope {
                        val mine = async { recipes().listMyRecipes().body() }
                        val library = async { recipes().listLibraryRecipes().body() }
                        val saved = collectionId?.let {
                            async { api().getRecipeCollection(it).body() }
                        }
                        val collection = saved?.await()
                        CollectionFormState.Editing(
                            saved = collection,
                            draft = CollectionDraft.of(collection),
                            recipes = mine.await() + library.await(),
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    CollectionFormState.Failed(describe(failure))
                }
        }
    }

    fun retry() = open(openedFor)

    fun close() {
        job?.cancel()
        mutableState.value = CollectionFormState.Closed
    }

    fun edit(change: (CollectionDraft) -> CollectionDraft) = changeEditing {
        if (it.busy) it else it.copy(draft = change(it.draft))
    }

    /** Shrinks [picked] as recipe pictures are, and makes it the cover; nothing is sent yet. */
    fun pickCover(picked: List<PickedPicture>) {
        val form = mutableState.value as? CollectionFormState.Editing ?: return
        val first = picked.firstOrNull() ?: return
        if (form.busy) return
        changeEditing { it.copy(shrinking = true, error = null) }
        job = scope.launch {
            val shrunk = shrinker.shrink(first)
            changeEditing {
                when (shrunk) {
                    is Shrunk.Ready ->
                        it.copy(
                            draft = it.draft.copy(cover = Cover.New(shrunk.prepared)),
                            shrinking = false,
                        )
                    is Shrunk.Failed -> it.copy(shrinking = false, error = shrunk.message)
                }
            }
        }
    }

    fun removeCover() = edit { it.copy(cover = Cover.None) }

    fun save() {
        val form = mutableState.value as? CollectionFormState.Editing ?: return
        if (form.busy) return
        val input =
            when (val checked = form.draft.check()) {
                is CheckedCollection.Invalid -> {
                    changeEditing { it.copy(error = checked.message) }
                    return
                }
                is CheckedCollection.Valid -> checked.input
            }
        changeEditing { it.copy(saving = true, error = null) }
        job = scope.launch {
            try {
                var saved =
                    form.saved?.let { api().replaceRecipeCollection(it.id, input).body() }
                        ?: api().createRecipeCollection(input).body()
                keep(saved)
                when (val cover = form.draft.cover) {
                    is Cover.New -> {
                        saved =
                            api()
                                .setRecipeCollectionPicture(
                                    saved.id,
                                    jpegFilePart(cover.prepared.jpeg),
                                )
                                .body()
                        val pictureId = saved.pictureId
                        changeEditing {
                            it.copy(
                                draft =
                                    it.draft.copy(
                                        cover = pictureId?.let(Cover::Saved) ?: Cover.None
                                    )
                            )
                        }
                        keep(saved)
                    }
                    Cover.None ->
                        if (saved.pictureId != null) {
                            api().deleteRecipeCollectionPicture(saved.id)
                            saved = saved.copy(pictureId = null)
                            keep(saved)
                        }
                    is Cover.Saved -> {}
                }
                if (form.draft.openByDefault != saved.isDefault) {
                    if (form.draft.openByDefault) api().setDefaultRecipeCollection(saved.id)
                    else api().clearDefaultRecipeCollection(saved.id)
                    saved = saved.copy(isDefault = form.draft.openByDefault)
                    keep(saved)
                }
                onSaved(saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                changeEditing { it.copy(saving = false, error = describe(failure)) }
            }
        }
    }

    /** Deletes the tab being edited; it drops out of every gallery and default with it. */
    fun delete() {
        val form = mutableState.value as? CollectionFormState.Editing ?: return
        val saved = form.saved ?: return
        if (form.busy) return
        changeEditing { it.copy(deleting = true, error = null) }
        job = scope.launch {
            try {
                api().deleteRecipeCollection(saved.id)
                onDeleted()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if ((failure as? ApiProblem)?.status == 404) onDeleted()
                else changeEditing { it.copy(deleting = false, error = describe(failure)) }
            }
        }
    }

    private fun keep(saved: RecipeCollection) = changeEditing { it.copy(saved = saved) }

    private fun changeEditing(
        change: (CollectionFormState.Editing) -> CollectionFormState.Editing
    ) = mutableState.update { if (it is CollectionFormState.Editing) change(it) else it }

    private fun describe(failure: Exception): String =
        if (failure is ApiProblem) failure.detail ?: "The API rejected the tab."
        else "Could not reach the server."
}
