package io.github.noahhhx.mimos.recipes

import io.github.noahhhx.mimos.api.apis.RecipeCollectionsApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.RecipeCollection
import io.github.noahhhx.mimos.api.models.RecipeDetail
import io.github.noahhhx.mimos.api.models.RecipeRatingInput
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.recipes.collections.CollectionFormModel
import io.github.noahhhx.mimos.recipes.form.RecipeFormModel
import io.github.noahhhx.mimos.recipes.pictures.PictureShrinker
import io.github.noahhhx.mimos.recipes.pictures.RecipePictures
import io.ktor.client.statement.readRawBytes
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

/** Keeps the chosen sort on this install, so it outlasts the app. */
interface RecipeSortStore {
    fun load(): RecipeSort?

    fun save(sort: RecipeSort)
}

sealed interface RecipeListState {
    data object Loading : RecipeListState

    data class Loaded(
        val mine: List<RecipeSummary>,
        val library: List<RecipeSummary>,
        val collections: List<RecipeCollection> = emptyList(),
        val members: Map<String, List<RecipeSummary>> = emptyMap(),
    ) : RecipeListState {
        fun of(tab: RecipeTab.Listing): List<RecipeSummary>? =
            when (tab) {
                RecipeTab.Mine -> mine
                RecipeTab.Library -> library
                is RecipeTab.Collection -> members[tab.id]
            }

        fun collection(id: String): RecipeCollection? = collections.firstOrNull { it.id == id }
    }

    data class Failed(val message: String) : RecipeListState
}

sealed interface RecipeDetailState {
    data object Loading : RecipeDetailState

    data class Loaded(val recipe: RecipeDetail) : RecipeDetailState

    data object NotFound : RecipeDetailState

    data class Failed(val message: String) : RecipeDetailState
}

sealed interface DeleteState {
    data object Idle : DeleteState

    data object Deleting : DeleteState

    data class Failed(val message: String) : DeleteState
}

sealed interface DefaultState {
    data object Idle : DefaultState

    data object Saving : DefaultState

    data class Failed(val message: String) : DefaultState
}

sealed interface RateState {
    data object Idle : RateState

    data object Saving : RateState

    data class Failed(val message: String) : RateState
}

/**
 * The Recipes tab: the library and the household's recipes, cooking from one, and the [form] that
 * writes them. It owns the route (see [RecipeRoute]), the list with its search, tab, sort, and tag
 * filter, the recipe on screen, and the [cookBook]. [api] is asked for on every call, because the
 * instance can change. [pictures] shows recipes' pictures and saves the form's.
 */
class RecipesModel(
    private val scope: CoroutineScope,
    private val sortStore: RecipeSortStore,
    private val api: () -> RecipesApi,
    private val collections: () -> RecipeCollectionsApi,
    val pictures: RecipePictures,
    shrinker: PictureShrinker,
    val cookBook: CookBook = CookBook(),
    private val searchDelayMillis: Long = SEARCH_DELAY_MILLIS,
) {
    private val mutableRoute = MutableStateFlow<RecipeRoute>(RecipeRoute.List)
    val route: StateFlow<RecipeRoute> = mutableRoute.asStateFlow()

    private var chosen: RecipeTab? = null
    private val mutableTab = MutableStateFlow<RecipeTab>(RecipeTab.Mine)

    val tab: StateFlow<RecipeTab> = mutableTab.asStateFlow()

    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()

    private val mutableSort = MutableStateFlow(sortStore.load() ?: RecipeSort.default)
    val sort: StateFlow<RecipeSort> = mutableSort.asStateFlow()

    private val mutableTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = mutableTags.asStateFlow()

    private val mutableList = MutableStateFlow<RecipeListState>(RecipeListState.Loading)
    val list: StateFlow<RecipeListState> = mutableList.asStateFlow()

    private val mutableDetail = MutableStateFlow<RecipeDetailState>(RecipeDetailState.Loading)
    val detail: StateFlow<RecipeDetailState> = mutableDetail.asStateFlow()

    private val mutableDeletion = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deletion: StateFlow<DeleteState> = mutableDeletion.asStateFlow()

    private val mutableRating = MutableStateFlow<RateState>(RateState.Idle)
    val rating: StateFlow<RateState> = mutableRating.asStateFlow()

    private val mutableDefaulting = MutableStateFlow<DefaultState>(DefaultState.Idle)
    val defaulting: StateFlow<DefaultState> = mutableDefaulting.asStateFlow()

    private var listJob: Job? = null
    private var membersJob: Job? = null
    private var detailJob: Job? = null

    val form = RecipeFormModel(scope, api, pictures, shrinker, onSaved = ::saved)

    val tabForm =
        CollectionFormModel(
            scope,
            api,
            collections,
            shrinker,
            onSaved = {
                chosen = RecipeTab.Collection(it.id)
                go(RecipeRoute.List)
            },
            onDeleted = {
                chosen = null
                go(RecipeRoute.List)
            },
        )

    /** Loads the lists the first time the tab is shown. */
    fun ensureLoaded() {
        if (list.value is RecipeListState.Loading && listJob?.isActive != true) refresh()
    }

    /** Loads both lists for the current search at once, keeping what is shown until they arrive. */
    fun refresh() = startLoading(waitFirst = false)

    fun search(text: String) {
        if (text == query.value) return
        mutableQuery.value = text
        startLoading(waitFirst = true)
    }

    fun selectTab(tab: RecipeTab) {
        chosen = tab
        val loaded = list.value as? RecipeListState.Loaded
        val shown = shownTab(tab, loaded?.collections.orEmpty())
        mutableTab.value = shown
        if (loaded != null && shown is RecipeTab.Collection && shown.id !in loaded.members) {
            loadMembers(shown.id)
        }
    }

    fun newTab() = go(RecipeRoute.NewTab)

    fun editTab() {
        (tab.value as? RecipeTab.Collection)?.let { go(RecipeRoute.EditTab(it.id)) }
    }

    fun toggleDefault(collectionId: String) {
        val collection = (list.value as? RecipeListState.Loaded)?.collection(collectionId) ?: return
        if (defaulting.value is DefaultState.Saving) return
        mutableDefaulting.value = DefaultState.Saving
        scope.launch {
            try {
                val on = !collection.isDefault
                if (on) collections().setDefaultRecipeCollection(collectionId)
                else collections().clearDefaultRecipeCollection(collectionId)
                mutableList.update { state ->
                    if (state !is RecipeListState.Loaded) state
                    else
                        state.copy(
                            collections =
                                state.collections.map {
                                    it.copy(isDefault = on && it.id == collectionId)
                                }
                        )
                }
                mutableDefaulting.value = DefaultState.Idle
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableDefaulting.value = DefaultState.Failed(describe(failure))
            }
        }
    }

    suspend fun cover(collectionId: String, pictureId: String): ByteArray =
        pictures.cached(pictureId) {
            collections()
                .getRecipeCollectionPicture(collectionId, pictureId)
                .response
                .readRawBytes()
        }

    fun chooseSort(sort: RecipeSort) {
        mutableSort.value = sort
        sortStore.save(sort)
    }

    fun toggleTag(tag: String) {
        mutableTags.value = selectedTags.value.let { if (tag in it) it - tag else it + tag }
    }

    fun clearTags() {
        mutableTags.value = emptySet()
    }

    /** Opens a recipe from the list, or follows an ingredient that is one. */
    fun open(id: RecipeId) = go(route.value.follow(id))

    /** Opens a recipe from another tab, starting a new trail. */
    fun show(id: RecipeId) = go(RecipeRoute.Recipe(emptyList(), id))

    /** Opens the form for a new recipe. */
    fun newRecipe() = go(RecipeRoute.New)

    /** Opens the form for the recipe on screen. */
    fun edit() {
        (route.value as? RecipeRoute.Recipe)?.let { go(RecipeRoute.Edit(it)) }
    }

    /** A new recipe opens on its own; an edited one returns to where it was opened from. */
    private fun saved(recipe: RecipeDetail) {
        when (val current = route.value) {
            RecipeRoute.New -> go(RecipeRoute.Recipe(emptyList(), RecipeId(recipe.id)))
            is RecipeRoute.Edit -> go(current.from)
            else -> {}
        }
    }

    /** Back one step: to the recipe followed from, or the list. False when already on the list. */
    fun back(): Boolean = route.value.back()?.also(::go) != null

    /** Back to the recipe at [index] of the trail, or to the list for a negative one. */
    fun goToCrumb(index: Int) {
        val current = route.value as? RecipeRoute.Recipe ?: return
        if (index < 0) go(RecipeRoute.List)
        else if (index < current.trail.size) {
            go(RecipeRoute.Recipe(current.trail.take(index), current.trail[index]))
        }
    }

    fun retryDetail() {
        (route.value as? RecipeRoute.Recipe)?.let { loadDetail(it.id) }
    }

    /** Deletes the recipe on screen, then returns to the list, reloaded. */
    fun delete(recipe: RecipeDetail) {
        if (deletion.value is DeleteState.Deleting) return
        mutableDeletion.value = DeleteState.Deleting
        scope.launch {
            try {
                api().deleteRecipe(recipe.id)
                cookBook.forget(RecipeId(recipe.id))
                go(RecipeRoute.List)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableDeletion.value = DeleteState.Failed(describe(failure))
            }
        }
    }

    /**
     * Rates the recipe on screen [star] stars, or clears its rating when [star] is the current one.
     */
    fun rate(recipe: RecipeDetail, star: Int) {
        if (rating.value is RateState.Saving) return
        mutableRating.value = RateState.Saving
        scope.launch {
            try {
                val stars = nextRating(recipe.rating, star)
                val rated =
                    if (stars == null) {
                        api().clearRecipeRating(recipe.id)
                        recipe.copy(rating = null)
                    } else {
                        api().rateRecipe(recipe.id, RecipeRatingInput(stars)).body()
                    }
                val shown = detail.value
                if (shown is RecipeDetailState.Loaded && shown.recipe.id == recipe.id) {
                    mutableDetail.value = RecipeDetailState.Loaded(rated)
                }
                mutableRating.value = RateState.Idle
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableRating.value = RateState.Failed(describe(failure))
            }
        }
    }

    /** Back to the start, for when the person signs out and someone else may sign in. */
    fun reset() {
        listJob?.cancel()
        membersJob?.cancel()
        detailJob?.cancel()
        mutableRoute.value = RecipeRoute.List
        chosen = null
        mutableTab.value = RecipeTab.Mine
        mutableQuery.value = ""
        mutableTags.value = emptySet()
        mutableList.value = RecipeListState.Loading
        mutableDetail.value = RecipeDetailState.Loading
        mutableDeletion.value = DeleteState.Idle
        mutableRating.value = RateState.Idle
        mutableDefaulting.value = DefaultState.Idle
        form.close()
        tabForm.close()
        cookBook.clear()
        pictures.clear()
    }

    private fun go(to: RecipeRoute) {
        detailJob?.cancel()
        mutableDeletion.value = DeleteState.Idle
        mutableRating.value = RateState.Idle
        mutableDefaulting.value = DefaultState.Idle
        mutableDetail.value = RecipeDetailState.Loading
        mutableRoute.value = to
        when (to) {
            RecipeRoute.List -> refresh()
            is RecipeRoute.Recipe -> loadDetail(to.id)
            RecipeRoute.New -> form.open(null)
            is RecipeRoute.Edit -> form.open(to.from.id.value)
            RecipeRoute.NewTab -> tabForm.open(null)
            is RecipeRoute.EditTab -> tabForm.open(to.collectionId)
        }
        if (to !is RecipeRoute.New && to !is RecipeRoute.Edit) form.close()
        if (to !is RecipeRoute.NewTab && to !is RecipeRoute.EditTab) tabForm.close()
    }

    private fun loadMembers(collectionId: String) {
        membersJob?.cancel()
        membersJob = scope.launch {
            try {
                val members = members(collectionId)
                mutableList.update { state ->
                    if (state !is RecipeListState.Loaded) state
                    else state.copy(members = state.members + (collectionId to members))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableList.value = RecipeListState.Failed(describe(failure))
            }
        }
    }

    private suspend fun members(collectionId: String): List<RecipeSummary> =
        collections().listRecipeCollectionRecipes(collectionId, searched()).body()

    private fun searched(): String? = query.value.trim().ifEmpty { null }

    private fun loadDetail(id: RecipeId) {
        detailJob?.cancel()
        mutableDetail.value = RecipeDetailState.Loading
        detailJob = scope.launch {
            mutableDetail.value =
                try {
                    val recipe = api().getRecipe(id.value).body()
                    cookBook.visit(id, recipe.title)
                    RecipeDetailState.Loaded(recipe)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if ((failure as? ApiProblem)?.status == 404) RecipeDetailState.NotFound
                    else RecipeDetailState.Failed(describe(failure))
                }
        }
    }

    /** A new search replaces the one before it, so a slow answer never lands after a newer one. */
    private fun startLoading(waitFirst: Boolean) {
        listJob?.cancel()
        membersJob?.cancel()
        listJob = scope.launch {
            if (waitFirst) delay(searchDelayMillis)
            val loaded =
                try {
                    val q = searched()
                    coroutineScope {
                        val mine = async { api().listMyRecipes(q).body() }
                        val library = async { api().listLibraryRecipes(q).body() }
                        val tabs = collections().listRecipeCollections().body()
                        val shown = shownTab(chosen, tabs)
                        val members =
                            if (shown is RecipeTab.Collection) mapOf(shown.id to members(shown.id))
                            else emptyMap()
                        RecipeListState.Loaded(mine.await(), library.await(), tabs, members)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    RecipeListState.Failed(describe(failure))
                }
            if (loaded is RecipeListState.Loaded) {
                mutableTab.value = shownTab(chosen, loaded.collections)
            }
            mutableList.value = loaded
        }
    }

    private fun describe(failure: Exception): String =
        if (failure is ApiProblem) "The API rejected the request."
        else "Could not reach the server."

    companion object {
        const val SEARCH_DELAY_MILLIS = 300L
    }
}
