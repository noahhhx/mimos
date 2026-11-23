package io.github.noahhhx.mimos.recipes.collections

import io.github.noahhhx.mimos.api.apis.RecipeCollectionsApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.RecipeCollection
import io.github.noahhhx.mimos.client.MimosHttp
import io.github.noahhhx.mimos.recipes.pictures.PickedPicture
import io.github.noahhhx.mimos.recipes.pictures.PictureShrinker
import io.github.noahhhx.mimos.recipes.pictures.PreparedPicture
import io.github.noahhhx.mimos.recipes.pictures.Shrunk
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private const val API = "https://api.mimos.example.com"

private fun summary(id: String, title: String, tags: String = "", library: Boolean = false) =
    """{"id":"$id","title":"$title","description":"d","servings":2,"isLibrary":$library,
        "createdAt":"2026-11-01T10:00:00Z","updatedAt":"2026-11-01T10:00:00Z",
        "tags":[$tags],"nutrition":{}}"""

private fun collection(
    id: String,
    name: String = "Soups",
    pictureId: String? = null,
    isDefault: Boolean = false,
) =
    """{"id":"$id","name":"$name","tags":["soup"],"recipeIds":["m1"],"isDefault":$isDefault,
        ${pictureId?.let { "\"pictureId\":\"$it\"," } ?: ""}"recipeCount":1,
        "createdAt":"2026-11-01T10:00:00Z"}"""

private class Server {
    /** Every write, in order: "POST /api/v1/recipe-collections", and the like. */
    val writes = mutableListOf<String>()
    val bodies = mutableListOf<String>()
    var pictureFails = false
    var deleteStatus = HttpStatusCode.NoContent
    var existing = collection("c1", pictureId = "p-old", isDefault = true)

    val engine = MockEngine { request ->
        val path = request.url.encodedPath
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        if (request.method != HttpMethod.Get) {
            writes += "${request.method.value} $path"
            if (!path.endsWith("/picture")) bodies += request.body.toByteArray().decodeToString()
        }
        when {
            path == "/api/v1/recipes" ->
                respond(
                    "[${summary("m1", "Soup", "\"Soup\"")},${summary("m2", "Bread")}]",
                    headers = json,
                )
            path == "/api/v1/recipes/library" ->
                respond(
                    "[${summary("l1", "Greek Salad", "\"quick\"", library = true)}]",
                    headers = json,
                )
            path == "/api/v1/recipe-collections" -> respond(collection("c-new"), headers = json)
            path.endsWith("/picture") && request.method == HttpMethod.Put ->
                if (pictureFails) respondError(HttpStatusCode.InternalServerError)
                else respond(collection("c-new", pictureId = "p-new"), headers = json)
            path.endsWith("/default") || request.method == HttpMethod.Delete ->
                if (deleteStatus.value < 300) respond("", HttpStatusCode.NoContent)
                else respondError(deleteStatus)
            path == "/api/v1/recipe-collections/c1" -> respond(existing, headers = json)
            else -> respond(collection(path.substringAfterLast('/')), headers = json)
        }
    }
}

private class FakeShrinker : PictureShrinker {
    override suspend fun shrink(picked: PickedPicture): Shrunk =
        if (picked.uri.endsWith("bad")) Shrunk.Failed("${picked.uri} can't be read as a picture.")
        else Shrunk.Ready(PreparedPicture(picked.uri.encodeToByteArray()))
}

private class Harness(scope: TestScope, val server: Server = Server()) {
    val saved = MutableStateFlow<RecipeCollection?>(null)
    val deleted = MutableStateFlow(false)
    val model =
        CollectionFormModel(
            scope,
            { MimosHttp(server.engine, API).api(::RecipesApi) },
            { MimosHttp(server.engine, API).api(::RecipeCollectionsApi) },
            FakeShrinker(),
            onSaved = { saved.value = it },
            onDeleted = { deleted.value = true },
        )

    suspend fun editing(until: (CollectionFormState.Editing) -> Boolean = { true }) =
        model.state.first { it is CollectionFormState.Editing && until(it) }
            as CollectionFormState.Editing
}

class CollectionFormModelTest {
    @Test
    fun `a new tab offers every recipe, own then library, and every tag in lowercase`() = runTest {
        val h = Harness(this)
        h.model.open(null)
        val form = h.editing()

        assertNull(form.saved)
        assertEquals(listOf("m1", "m2", "l1"), form.recipes.map { it.id })
        assertEquals(listOf("quick", "soup"), tagChoices(form.recipes, form.draft.tags))
    }

    @Test
    fun `creating sends the fields, then the cover, then the default, and opens the tab`() =
        runTest {
            val h = Harness(this)
            h.model.open(null)
            h.editing()
            h.model.edit {
                it.copy(name = "  Soups ", openByDefault = true).toggleRecipe("m1").addTag(" Soup")
            }
            h.model.pickCover(listOf(PickedPicture("content://cover")))
            h.editing { it.draft.cover is Cover.New }

            h.model.save()
            val saved = h.saved.first { it != null }!!

            assertEquals(
                listOf(
                    "POST /api/v1/recipe-collections",
                    "PUT /api/v1/recipe-collections/c-new/picture",
                    "PUT /api/v1/recipe-collections/c-new/default",
                ),
                h.server.writes,
            )
            assertEquals(
                """{"name":"Soups","tags":["soup"],"recipeIds":["m1"]}""",
                h.server.bodies.first().filterNot { it.isWhitespace() },
            )
            assertEquals("p-new", saved.pictureId)
            assertTrue(saved.isDefault)
        }

    @Test
    fun `a failed cover keeps the tab it made, so saving again replaces it`() = runTest {
        val h = Harness(this, Server().apply { pictureFails = true })
        h.model.open(null)
        h.editing()
        h.model.edit { it.copy(name = "Soups") }
        h.model.pickCover(listOf(PickedPicture("content://cover")))
        h.editing { it.draft.cover is Cover.New }

        h.model.save()
        val failed = h.editing { !it.saving && it.error != null }
        assertEquals("c-new", failed.saved?.id)

        h.server.pictureFails = false
        h.model.save()
        h.saved.first { it != null }

        assertEquals(
            listOf(
                "POST /api/v1/recipe-collections",
                "PUT /api/v1/recipe-collections/c-new/picture",
                "PUT /api/v1/recipe-collections/c-new",
                "PUT /api/v1/recipe-collections/c-new/picture",
            ),
            h.server.writes,
        )
    }

    @Test
    fun `editing removes the cover and the default when they are taken away`() = runTest {
        val h = Harness(this)
        h.model.open("c1")
        val form = h.editing()
        assertEquals(Cover.Saved("p-old"), form.draft.cover)
        assertTrue(form.draft.openByDefault)

        h.model.removeCover()
        h.model.edit { it.copy(openByDefault = false) }
        h.model.save()
        val saved = h.saved.first { it != null }!!

        assertEquals(
            listOf(
                "PUT /api/v1/recipe-collections/c1",
                "DELETE /api/v1/recipe-collections/c1/picture",
                "DELETE /api/v1/recipe-collections/c1/default",
            ),
            h.server.writes,
        )
        assertNull(saved.pictureId)
        assertEquals(false, saved.isDefault)
    }

    @Test
    fun `an edit that keeps the cover and default sends only the fields`() = runTest {
        val h = Harness(this)
        h.model.open("c1")
        h.editing()

        h.model.edit { it.copy(name = "Soups and stews") }
        h.model.save()
        h.saved.first { it != null }

        assertEquals(listOf("PUT /api/v1/recipe-collections/c1"), h.server.writes)
    }

    @Test
    fun `a blank name is refused before anything is sent`() = runTest {
        val h = Harness(this)
        h.model.open(null)
        h.editing()

        h.model.edit { it.copy(name = "   ") }
        h.model.save()

        assertEquals("Give the tab a name.", h.editing().error)
        assertEquals(emptyList(), h.server.writes)
    }

    @Test
    fun `a picture that cannot be read says so and keeps the cover as it was`() = runTest {
        val h = Harness(this)
        h.model.open("c1")
        h.editing()

        h.model.pickCover(listOf(PickedPicture("content://bad")))
        val form = h.editing { !it.shrinking && it.error != null }

        assertEquals("content://bad can't be read as a picture.", form.error)
        assertEquals(Cover.Saved("p-old"), form.draft.cover)
    }

    @Test
    fun `deleting tells the list, and a tab already gone counts as deleted`() = runTest {
        val h = Harness(this, Server().apply { deleteStatus = HttpStatusCode.NotFound })
        h.model.open("c1")
        h.editing()

        h.model.delete()

        assertTrue(h.deleted.first { it })
        assertEquals(listOf("DELETE /api/v1/recipe-collections/c1"), h.server.writes)
    }

    @Test
    fun `a refused delete stays on the form and says so`() = runTest {
        val h = Harness(this, Server().apply { deleteStatus = HttpStatusCode.Forbidden })
        h.model.open("c1")
        h.editing()

        h.model.delete()

        assertIs<String>(h.editing { !it.deleting && it.error != null }.error)
        assertEquals(false, h.deleted.value)
    }
}
