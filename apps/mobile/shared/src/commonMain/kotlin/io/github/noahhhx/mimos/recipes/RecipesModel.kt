package io.github.noahhhx.mimos.recipes

import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.RecipeDetail
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.client.ApiProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Keeps the chosen sort on this install, so it outlasts the app. */
interface RecipeSortStore {
    fun load(): RecipeSort?

    fun save(sort: RecipeSort)
}

enum class RecipeTab(val label: String) {
    Mine("My recipes"),
    Library("Library"),
}

sealed interface RecipeListState {
    data object Loading : RecipeListState

    /** Both lists, as searched: the tabs name how many each holds. */
    data class Loaded(val mine: List<RecipeSummary>, val library: List<RecipeSummary>) :
        RecipeListState {
        fun of(tab: RecipeTab): List<RecipeSummary> =
            when (tab) {
                RecipeTab.Mine -> mine
                RecipeTab.Library -> library
            }
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

/**
 * The Recipes tab: the library and the household's recipes, and cooking from one. It owns the route
 * (see [RecipeRoute]), the list with its search, tab, sort, and tag filter, the recipe on screen,
 * and the [cookBook]. [api] is asked for on every call, because the instance can change.
 */
class RecipesModel(
    private val scope: CoroutineScope,
    private val sortStore: RecipeSortStore,
    private val api: () -> RecipesApi,
    val cookBook: CookBook = CookBook(),
    private val searchDelayMillis: Long = SEARCH_DELAY_MILLIS,
) {
    private val mutableRoute = MutableStateFlow<RecipeRoute>(RecipeRoute.List)
    val route: StateFlow<RecipeRoute> = mutableRoute.asStateFlow()

    private val mutableTab = MutableStateFlow(RecipeTab.Mine)
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

    private var listJob: Job? = null
    private var detailJob: Job? = null

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
        mutableTab.value = tab
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

    /** Back to the start, for when the person signs out and someone else may sign in. */
    fun reset() {
        listJob?.cancel()
        detailJob?.cancel()
        mutableRoute.value = RecipeRoute.List
        mutableTab.value = RecipeTab.Mine
        mutableQuery.value = ""
        mutableTags.value = emptySet()
        mutableList.value = RecipeListState.Loading
        mutableDetail.value = RecipeDetailState.Loading
        mutableDeletion.value = DeleteState.Idle
        cookBook.clear()
    }

    private fun go(to: RecipeRoute) {
        detailJob?.cancel()
        mutableDeletion.value = DeleteState.Idle
        mutableDetail.value = RecipeDetailState.Loading
        mutableRoute.value = to
        when (to) {
            RecipeRoute.List -> refresh()
            is RecipeRoute.Recipe -> loadDetail(to.id)
        }
    }

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
        listJob = scope.launch {
            if (waitFirst) delay(searchDelayMillis)
            mutableList.value =
                try {
                    val q = query.value.trim().ifEmpty { null }
                    coroutineScope {
                        val mine = async { api().listMyRecipes(q).body() }
                        val library = async { api().listLibraryRecipes(q).body() }
                        RecipeListState.Loaded(mine.await(), library.await())
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    RecipeListState.Failed(describe(failure))
                }
        }
    }

    private fun describe(failure: Exception): String =
        if (failure is ApiProblem) "The API rejected the request."
        else "Could not reach the server."

    companion object {
        const val SEARCH_DELAY_MILLIS = 300L
    }
}
