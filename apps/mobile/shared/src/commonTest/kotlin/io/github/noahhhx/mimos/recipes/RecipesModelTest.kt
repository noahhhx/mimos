package io.github.noahhhx.mimos.recipes

import io.github.noahhhx.mimos.api.apis.RecipePicturesApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.client.MimosHttp
import io.github.noahhhx.mimos.recipes.form.RecipeFormState
import io.github.noahhhx.mimos.recipes.pictures.PickedPicture
import io.github.noahhhx.mimos.recipes.pictures.PictureShrinker
import io.github.noahhhx.mimos.recipes.pictures.RecipePictures
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private const val API = "https://api.mimos.example.com"

private fun summary(id: String, title: String, tags: String = "") =
    """{"id":"$id","title":"$title","description":"d","servings":2,"isLibrary":false,
        "createdAt":"2026-11-01T10:00:00Z","updatedAt":"2026-11-01T10:00:00Z",
        "tags":[${tags}],"nutrition":{"calories":400}}"""

private fun detail(id: String, title: String) =
    """{"id":"$id","title":"$title","description":"d","servings":2,"isLibrary":false,
        "tags":[],"nutrition":{},"nutritionSource":"MANUAL",
        "ingredients":[{"name":"flour","quantity":200,"unit":"g"}],
        "steps":[{"instruction":"Mix."}],"pictures":[]}"""

private class MemorySortStore(var saved: RecipeSort? = null) : RecipeSortStore {
    override fun load() = saved

    override fun save(sort: RecipeSort) {
        saved = sort
    }
}

private class Server {
    /** Every request as it arrives, for a test to read on its own thread. */
    val requests = Channel<HttpRequestData>(Channel.UNLIMITED)
    var mine = "[${summary("m1", "Soup")}]"
    var library = "[${summary("l1", "Greek Salad")},${summary("l2", "Shakshuka")}]"
    var failList = false
    var deleteStatus = HttpStatusCode.NoContent
    val missing = mutableSetOf<String>()

    /** The `q` of each request for the household's own recipes, as it arrives. */
    val searched = Channel<String?>(Channel.UNLIMITED)

    val engine = MockEngine { request ->
        requests.trySend(request)
        val path = request.url.encodedPath
        if (path == "/api/v1/recipes") searched.trySend(request.url.parameters["q"])
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            failList && path.endsWith("/recipes") ->
                respondError(HttpStatusCode.InternalServerError)
            path == "/api/v1/ingredients" -> respond("[]", headers = json)
            path == "/api/v1/recipes/nutrition-estimate" ->
                respond("""{"nutrition":{},"lines":[]}""", headers = json)
            path == "/api/v1/recipes" && request.method == HttpMethod.Post ->
                respond(detail("created", "Made"), headers = json)
            path == "/api/v1/recipes" -> respond(mine, headers = json)
            path == "/api/v1/recipes/library" -> respond(library, headers = json)
            request.method == HttpMethod.Delete ->
                if (deleteStatus.value < 300) respond("", deleteStatus)
                else respondError(deleteStatus)
            else -> {
                val id = path.substringAfterLast('/')
                if (id in missing) respondError(HttpStatusCode.NotFound)
                else respond(detail(id, "Recipe $id"), headers = json)
            }
        }
    }

    suspend fun awaitRequest(method: HttpMethod, path: String) {
        while (true) {
            val request = requests.receive()
            if (request.method == method && request.url.encodedPath == path) return
        }
    }
}

private fun TestScope.model(server: Server, store: RecipeSortStore = MemorySortStore()) =
    RecipesModel(
        this,
        store,
        { MimosHttp(server.engine, API).api(::RecipesApi) },
        RecipePictures({ MimosHttp(server.engine, API).api(::RecipePicturesApi) }),
        object : PictureShrinker {
            override suspend fun shrink(picked: PickedPicture) = error("nothing is picked")
        },
    )

class RecipesModelTest {
    @Test
    fun `the first load reads both lists and the tabs count them`() = runTest {
        val server = Server()
        val model = model(server)

        model.ensureLoaded()
        val loaded = model.list.first { it !is RecipeListState.Loading }

        assertIs<RecipeListState.Loaded>(loaded)
        assertEquals(listOf("m1"), loaded.of(RecipeTab.Mine).map { it.id })
        assertEquals(listOf("l1", "l2"), loaded.of(RecipeTab.Library).map { it.id })
        assertEquals(RecipeTab.Mine, model.tab.value)
    }

    @Test
    fun `a failed load says so and a retry loads`() = runTest {
        val server = Server().apply { failList = true }
        val model = model(server)

        model.ensureLoaded()
        assertIs<RecipeListState.Failed>(model.list.first { it !is RecipeListState.Loading })

        server.failList = false
        model.refresh()
        assertIs<RecipeListState.Loaded>(model.list.first { it is RecipeListState.Loaded })
    }

    @Test
    fun `typing searches once, after the pause, with only the last text`() = runTest {
        val server = Server()
        val model = model(server)
        model.ensureLoaded()
        assertNull(server.searched.receive())

        model.search("g")
        model.search("gr")
        model.search("greek")

        assertEquals("greek", server.searched.receive())
        assertEquals("greek", model.query.value)
        model.list.first { it is RecipeListState.Loaded }
        assertTrue(server.searched.tryReceive().isFailure, "only the last text was searched")
    }

    @Test
    fun `a blank search asks for no query`() = runTest {
        val server = Server()
        val model = model(server)
        model.search("  ")
        model.ensureLoaded()

        assertNull(server.searched.receive())
        model.list.first { it is RecipeListState.Loaded }
        assertTrue(server.searched.tryReceive().isFailure)
    }

    @Test
    fun `the sort is read from the store and saved when chosen`() = runTest {
        val store = MemorySortStore(RecipeSort.Oldest)
        val model = model(Server(), store)
        assertEquals(RecipeSort.Oldest, model.sort.value)

        model.chooseSort(RecipeSort.Edited)

        assertEquals(RecipeSort.Edited, model.sort.value)
        assertEquals(RecipeSort.Edited, store.saved)
    }

    @Test
    fun `tags toggle and clear`() = runTest {
        val model = model(Server())
        model.toggleTag("quick")
        model.toggleTag("soup")
        model.toggleTag("quick")
        assertEquals(setOf("soup"), model.selectedTags.value)

        model.clearTags()
        assertEquals(emptySet(), model.selectedTags.value)
    }

    @Test
    fun `opening a recipe loads it and names it in the cook book`() = runTest {
        val model = model(Server())

        model.open(RecipeId("r1"))
        val state = model.detail.first { it !is RecipeDetailState.Loading }

        assertIs<RecipeDetailState.Loaded>(state)
        assertEquals("r1", state.recipe.id)
        assertEquals(RecipeRoute.Recipe(emptyList(), RecipeId("r1")), model.route.value)
        assertEquals("Recipe r1", model.cookBook.title(RecipeId("r1")))
    }

    @Test
    fun `following a link pushes and back pops, keeping the ticks`() = runTest {
        val model = model(Server())
        model.open(RecipeId("sandwich"))
        model.detail.first { it is RecipeDetailState.Loaded }
        model.cookBook.toggleStep(RecipeId("sandwich"), 0)

        model.open(RecipeId("focaccia"))
        assertEquals(
            RecipeRoute.Recipe(listOf(RecipeId("sandwich")), RecipeId("focaccia")),
            model.route.value,
        )
        assertEquals(RecipeDetailState.Loading, model.detail.value)
        model.detail.first { it is RecipeDetailState.Loaded }

        assertTrue(model.back())
        assertEquals(RecipeRoute.Recipe(emptyList(), RecipeId("sandwich")), model.route.value)
        model.detail.first { it is RecipeDetailState.Loaded }
        assertEquals(setOf(0), model.cookBook.read(RecipeId("sandwich"), 1, 1).steps)

        assertTrue(model.back())
        assertEquals(RecipeRoute.List, model.route.value)
        assertEquals(false, model.back())
    }

    @Test
    fun `a crumb returns to its recipe and the first crumb to the list`() = runTest {
        val model = model(Server())
        model.open(RecipeId("a"))
        model.open(RecipeId("b"))
        model.open(RecipeId("c"))

        model.goToCrumb(1)
        assertEquals(RecipeRoute.Recipe(listOf(RecipeId("a")), RecipeId("b")), model.route.value)

        model.goToCrumb(-1)
        assertEquals(RecipeRoute.List, model.route.value)
    }

    @Test
    fun `a recipe that is not there is not found`() = runTest {
        val server = Server().apply { missing += "gone" }
        val model = model(server)

        model.open(RecipeId("gone"))

        assertEquals(
            RecipeDetailState.NotFound,
            model.detail.first { it !is RecipeDetailState.Loading },
        )
    }

    @Test
    fun `deleting calls the API, returns to a reloaded list, and forgets the ticks`() = runTest {
        val server = Server()
        val model = model(server)
        model.open(RecipeId("m1"))
        val recipe =
            (model.detail.first { it is RecipeDetailState.Loaded } as RecipeDetailState.Loaded)
                .recipe
        model.cookBook.toggleStep(RecipeId("m1"), 0)
        model.delete(recipe)
        server.awaitRequest(HttpMethod.Delete, "/api/v1/recipes/m1")
        server.searched.receive()

        assertEquals(RecipeRoute.List, model.route.value)
        assertNull(model.cookBook.title(RecipeId("m1")))
    }

    @Test
    fun `a refused delete stays on the recipe and says so`() = runTest {
        val server = Server().apply { deleteStatus = HttpStatusCode.Forbidden }
        val model = model(server)
        model.open(RecipeId("m1"))
        val recipe =
            (model.detail.first { it is RecipeDetailState.Loaded } as RecipeDetailState.Loaded)
                .recipe

        model.delete(recipe)
        model.deletion.first { it is DeleteState.Failed }

        assertEquals(RecipeRoute.Recipe(emptyList(), RecipeId("m1")), model.route.value)
    }

    @Test
    fun `resetting returns to a fresh list and clears the cook book`() = runTest {
        val model = model(Server())
        model.ensureLoaded()
        model.list.first { it is RecipeListState.Loaded }
        model.selectTab(RecipeTab.Library)
        model.search("x")
        model.toggleTag("quick")
        model.open(RecipeId("r1"))
        model.cookBook.visit(RecipeId("r1"), "R1")

        model.reset()

        assertEquals(RecipeRoute.List, model.route.value)
        assertEquals(RecipeTab.Mine, model.tab.value)
        assertEquals("", model.query.value)
        assertEquals(emptySet(), model.selectedTags.value)
        assertEquals(RecipeListState.Loading, model.list.value)
        assertEquals(RecipeDetailState.Loading, model.detail.value)
        assertNull(model.cookBook.title(RecipeId("r1")))
    }

    @Test
    fun `a new recipe's form opens from the list, and saving it opens the recipe`() = runTest {
        val server = Server()
        val model = model(server)

        model.newRecipe()
        assertEquals(RecipeRoute.New, model.route.value)
        model.form.state.first { it is RecipeFormState.Editing }
        model.form.edit { it.copy(title = "Made", description = "d", steps = listOf("Mix.")) }
        model.form.editRow(0) { it.copy(name = "flour") }
        model.form.save()
        server.awaitRequest(HttpMethod.Post, "/api/v1/recipes")

        assertEquals(
            RecipeRoute.Recipe(emptyList(), RecipeId("created")),
            model.route.first { it is RecipeRoute.Recipe },
        )
        assertEquals(RecipeFormState.Closed, model.form.state.value)
        assertTrue(model.back())
        assertEquals(RecipeRoute.List, model.route.value)
    }

    @Test
    fun `editing returns to the recipe it was opened from, reloaded, on save or back`() = runTest {
        val server = Server()
        val model = model(server)
        model.open(RecipeId("a"))
        model.open(RecipeId("m1"))
        val from = model.route.value as RecipeRoute.Recipe

        model.edit()
        assertEquals(RecipeRoute.Edit(from), model.route.value)
        model.form.state.first { it is RecipeFormState.Editing }
        model.form.save()
        server.awaitRequest(HttpMethod.Put, "/api/v1/recipes/m1")
        assertEquals(from, model.route.first { it is RecipeRoute.Recipe })
        server.awaitRequest(HttpMethod.Get, "/api/v1/recipes/m1")

        model.edit()
        assertTrue(model.back())
        assertEquals(from, model.route.value)
        assertEquals(RecipeFormState.Closed, model.form.state.value)
    }
}
