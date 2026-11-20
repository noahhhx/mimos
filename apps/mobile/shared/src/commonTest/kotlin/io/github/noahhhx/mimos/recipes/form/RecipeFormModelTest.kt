package io.github.noahhhx.mimos.recipes.form

import io.github.noahhhx.mimos.api.apis.RecipePicturesApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.IngredientLineStatus
import io.github.noahhhx.mimos.api.models.NutritionSource
import io.github.noahhhx.mimos.api.models.RecipeDetail
import io.github.noahhhx.mimos.client.MimosHttp
import io.github.noahhhx.mimos.ingredients.NewIngredientDraft
import io.github.noahhhx.mimos.recipes.pictures.PickedPicture
import io.github.noahhhx.mimos.recipes.pictures.PictureShrinker
import io.github.noahhhx.mimos.recipes.pictures.PictureSlot
import io.github.noahhhx.mimos.recipes.pictures.PreparedPicture
import io.github.noahhhx.mimos.recipes.pictures.RecipePictures
import io.github.noahhhx.mimos.recipes.pictures.Shrunk
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val API = "https://api.mimos.example.com"

private const val CATALOG =
    """[{"slug":"olive-oil","name":"Olive oil","isShared":true,"basis":"PER_100_ML",
        "nutrition":{"calories":813,"proteinG":0,"carbsG":0,"fatG":92}},
       {"slug":"garlic-clove","name":"Garlic clove","isShared":true,"basis":"PER_PIECE",
        "nutrition":{"calories":4,"proteinG":0,"carbsG":1,"fatG":0}}]"""

private fun summary(id: String, title: String) =
    """{"id":"$id","title":"$title","description":"d","servings":8,"isLibrary":false,
        "createdAt":"2026-11-01T10:00:00Z","updatedAt":"2026-11-01T10:00:00Z",
        "tags":[],"nutrition":{"calories":278,"proteinG":6,"carbsG":47,"fatG":6}}"""

private fun detail(id: String, title: String) =
    """{"id":"$id","title":"$title","description":"Warm.","servings":2,"isLibrary":false,
        "tags":["soup"],"nutrition":{},"nutritionSource":"INGREDIENTS",
        "ingredients":[{"name":"olive oil","quantity":1,"unit":"tbsp","catalogSlug":"olive-oil"}],
        "steps":[{"instruction":"Simmer."}],"pictures":[]}"""

/** A request as the server saw it: its method, path, JSON body, and other body as text. */
private class Seen(
    val method: HttpMethod,
    val path: String,
    val body: JsonObject?,
    val contentType: String? = null,
    val raw: String? = null,
)

/** Shrinks a picked picture to its URI's bytes; one whose URI ends in "bad" cannot be read. */
private class FakeShrinker : PictureShrinker {
    override suspend fun shrink(picked: PickedPicture): Shrunk =
        if (picked.uri.endsWith("bad")) Shrunk.Failed("${picked.uri} can't be read as a picture.")
        else Shrunk.Ready(PreparedPicture(picked.uri.encodeToByteArray()))
}

private class Server {
    val seen = mutableListOf<Seen>()
    var recipeStatus = HttpStatusCode.OK
    var problem: String? = null
    var estimateFails = false
    var pictureFails = false
    private var uploads = 0

    val engine = MockEngine { request ->
        val body =
            (request.body as? TextContent)?.text?.let { Json.parseToJsonElement(it).jsonObject }
        val path = request.url.encodedPath
        val raw = if (body == null) request.body.toByteArray().decodeToString() else null
        seen += Seen(request.method, path, body, request.body.contentType?.toString(), raw)
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            path.endsWith("/pictures") && pictureFails ->
                respond(
                    """{"title":"Bad Request","status":400,"detail":"Not a picture."}""",
                    HttpStatusCode.BadRequest,
                    headersOf(HttpHeaders.ContentType, "application/problem+json"),
                )
            path.endsWith("/pictures") ->
                respond("""{"id":"picture-${++uploads}"}""", HttpStatusCode.Created, json)
            path == "/api/v1/ingredients" && request.method == HttpMethod.Get ->
                respond(CATALOG, headers = json)
            path == "/api/v1/ingredients" ->
                respond(
                    """{"slug":"dragon-fruit-x1","name":"${body!!["name"]!!.jsonPrimitive.content}",
                        "isShared":false,"basis":"PER_PIECE","nutrition":{"calories":60}}""",
                    headers = json,
                )
            path == "/api/v1/recipes/nutrition-estimate" ->
                if (estimateFails) respondError(HttpStatusCode.InternalServerError)
                else {
                    val lines = body!!["ingredients"]!!.jsonArray.map { "\"COUNTED\"" }
                    respond(
                        """{"nutrition":{"calories":100},"lines":$lines}""",
                        headers = json,
                    )
                }
            path == "/api/v1/recipes" && request.method == HttpMethod.Get ->
                respond(
                    "[${summary("r-focaccia", "Focaccia")},${summary("r1", "Soup")}]",
                    headers = json,
                )
            problem != null ->
                respond(
                    problem!!,
                    HttpStatusCode.BadRequest,
                    headersOf(HttpHeaders.ContentType, "application/problem+json"),
                )
            path == "/api/v1/recipes" -> respond(detail("new-id", "Made"), headers = json)
            else -> respond(detail(path.substringAfterLast('/'), "Soup"), recipeStatus, json)
        }
    }

    fun estimates() = seen.filter { it.path == "/api/v1/recipes/nutrition-estimate" }

    /** What changed the recipe, in order: "POST /api/v1/recipes", and the like. */
    fun writes() =
        seen
            .filter { it.method != HttpMethod.Get && !it.path.endsWith("/nutrition-estimate") }
            .map { "${it.method.value} ${it.path}" }
}

private class Harness(scope: TestScope, val server: Server = Server()) {
    val saved = MutableStateFlow<List<RecipeDetail>>(emptyList())
    val model =
        RecipeFormModel(
            scope,
            { MimosHttp(server.engine, API).api(::RecipesApi) },
            RecipePictures({ MimosHttp(server.engine, API).api(::RecipePicturesApi) }),
            FakeShrinker(),
            onSaved = { saved.value += it },
        )

    fun editing() = model.state.value as RecipeFormState.Editing

    /** The form once its estimate has an answer, or has nothing to ask. */
    suspend fun settled() =
        model.state.first { it is RecipeFormState.Editing && it.estimate !is Estimate.Pending }
            as RecipeFormState.Editing

    suspend fun savedIds() = saved.first { it.isNotEmpty() }.map { it.id }
}

private suspend fun RecipeFormModel.awaitEditing() =
    state.first { it is RecipeFormState.Editing } as RecipeFormState.Editing

class RecipeFormModelTest {
    @Test
    fun `a new recipe opens with the catalog, the household's recipes, and an empty form`() =
        runTest {
            val h = Harness(this)
            h.model.open(null)
            val form = h.model.awaitEditing()

            assertNull(form.recipeId)
            assertEquals(RecipeDraft(), form.draft)
            assertEquals(listOf("olive-oil", "garlic-clove"), form.catalog.map { it.slug })
            assertEquals(listOf("r-focaccia", "r1"), form.recipes.map { it.id })
        }

    @Test
    fun `an edit opens the recipe's own values and never offers the recipe to itself`() = runTest {
        val h = Harness(this)
        h.model.open("r1")
        val form = h.model.awaitEditing()

        assertEquals("r1", form.recipeId)
        assertEquals("soup", form.draft.tags)
        assertEquals(RowLink.Entry("olive-oil"), form.draft.ingredients.single().link)
        assertEquals(listOf("r-focaccia"), form.recipes.map { it.id })
    }

    @Test
    fun `a form that cannot load says so, and a retry loads it`() = runTest {
        val h = Harness(this)
        h.server.recipeStatus = HttpStatusCode.NotFound
        h.model.open("gone")
        assertIs<RecipeFormState.Failed>(
            h.model.state.first { it is RecipeFormState.Failed || it is RecipeFormState.Editing }
        )

        h.server.recipeStatus = HttpStatusCode.OK
        h.model.retry()
        assertEquals("gone", h.model.awaitEditing().recipeId)
    }

    @Test
    fun `typing a name links the entry it names, and the estimate is asked once, after the pause`() =
        runTest {
            val h = Harness(this)
            h.model.open(null)
            h.settled()
            val before = h.server.estimates().size

            h.model.editRow(0) { it.copy(quantity = "2", unit = "tbsp") }
            for (name in listOf("o", "ol", "olive", "olive oil")) h.model.typeName(0, name)
            h.model.edit { it.copy(title = "Dressing") }
            assertIs<Estimate.Pending>(h.editing().estimate)
            val form = h.settled()

            assertEquals(RowLink.Entry("olive-oil"), form.draft.ingredients[0].link)
            val asked = h.server.estimates().drop(before)
            assertEquals(1, asked.size, "one request for the last change only")
            val line = asked.single().body!!["ingredients"]!!.jsonArray.single().jsonObject
            assertEquals("olive-oil", line["catalogSlug"]!!.jsonPrimitive.content)
            val ready = assertIs<Estimate.Ready>(form.estimate)
            assertEquals(mapOf(0 to IngredientLineStatus.COUNTED), ready.statuses)

            h.model.edit { it.copy(title = "Vinaigrette") }
            assertEquals(
                ready,
                h.editing().estimate,
                "a change the estimate is not sent asks nothing",
            )
        }

    @Test
    fun `statuses land on the form rows they came from`() = runTest {
        val h = Harness(this)
        h.model.open(null)
        h.model.awaitEditing()
        h.model.addIngredient()
        h.model.editRow(1) { it.copy(quantity = "1", unit = "", name = "garlic clove") }

        assertEquals(
            mapOf(1 to IngredientLineStatus.COUNTED),
            assertIs<Estimate.Ready>(h.settled().estimate).statuses,
        )
    }

    @Test
    fun `typed nutrition needs no estimate, and a failed estimate says so`() = runTest {
        val h = Harness(this)
        h.server.estimateFails = true
        h.model.open(null)
        assertEquals(Estimate.Failed, h.settled().estimate)
        val asked = h.server.estimates().size

        h.model.edit { it.copy(nutritionSource = NutritionSource.MANUAL) }
        h.model.editRow(0) { it.copy(name = "salt") }
        advanceUntilIdle()

        assertEquals(Estimate.None, h.editing().estimate)
        assertEquals(asked, h.server.estimates().size)
    }

    @Test
    fun `saving an invalid form shows every problem and sends nothing`() = runTest {
        val h = Harness(this)
        h.model.open(null)
        h.model.awaitEditing()
        h.model.save()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "Give the recipe a title.",
                "Give the recipe a description.",
                "Add at least one ingredient.",
                "Add at least one step.",
            ),
            h.editing().errors,
        )
        assertTrue(
            h.server.seen.none { it.method == HttpMethod.Post && it.path == "/api/v1/recipes" }
        )
        assertTrue(h.saved.value.isEmpty())
    }

    @Test
    fun `saving a new recipe creates it and hands it over`() = runTest {
        val h = Harness(this)
        h.model.open(null)
        h.model.awaitEditing()
        fill(h.model)
        h.model.save()

        assertEquals(listOf("new-id"), h.savedIds())
        val created =
            h.server.seen.single { it.method == HttpMethod.Post && it.path == "/api/v1/recipes" }
        assertEquals("Soup", created.body!!["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `saving an edit replaces the recipe`() = runTest {
        val h = Harness(this)
        h.model.open("r1")
        h.model.awaitEditing()
        h.model.save()

        assertEquals(listOf("r1"), h.savedIds())
        assertTrue(
            h.server.seen.any { it.method == HttpMethod.Put && it.path == "/api/v1/recipes/r1" }
        )
    }

    @Test
    fun `picked pictures are shrunk at once, and uploaded after the recipe is created`() = runTest {
        val h = Harness(this)
        h.model.open(null)
        h.model.awaitEditing()
        fill(h.model)
        h.model.addPictures(listOf(PickedPicture("content://one"), PickedPicture("content://bad")))
        val picked =
            h.model.state.first { !(it as RecipeFormState.Editing).shrinking }
                as RecipeFormState.Editing

        assertEquals(listOf("content://bad can't be read as a picture."), picked.pictureProblems)
        assertEquals(1, picked.pictures.slots.size)
        assertTrue(h.server.writes().isEmpty())

        h.model.save()

        assertEquals(listOf("new-id"), h.savedIds())
        assertEquals(
            listOf("POST /api/v1/recipes", "POST /api/v1/recipes/new-id/pictures"),
            h.server.writes(),
        )
        val upload = h.server.seen.single { it.path == "/api/v1/recipes/new-id/pictures" }
        assertTrue(upload.contentType!!.startsWith("multipart/form-data"), upload.contentType)
        assertTrue("name=\"file\"" in upload.raw!! && "filename=" in upload.raw, upload.raw)
        assertTrue("content://one" in upload.raw)
    }

    @Test
    fun `a picture that fails keeps the form on the saved recipe, so saving again replaces it`() =
        runTest {
            val h = Harness(this)
            h.server.pictureFails = true
            h.model.open(null)
            h.model.awaitEditing()
            fill(h.model)
            h.model.addPictures(listOf(PickedPicture("content://one")))
            h.model.state.first { (it as RecipeFormState.Editing).pictures.slots.isNotEmpty() }
            h.model.save()
            val stopped =
                h.model.state.first { (it as RecipeFormState.Editing).errors.isNotEmpty() }
                    as RecipeFormState.Editing

            assertEquals(listOf("Not a picture."), stopped.errors)
            assertEquals("new-id", stopped.recipeId)
            assertIs<PictureSlot.New>(stopped.pictures.slots.single())
            assertTrue(h.saved.value.isEmpty())

            h.server.pictureFails = false
            h.model.save()

            assertEquals(listOf("new-id"), h.savedIds())
            assertEquals(
                listOf(
                    "POST /api/v1/recipes",
                    "POST /api/v1/recipes/new-id/pictures",
                    "PUT /api/v1/recipes/new-id",
                    "POST /api/v1/recipes/new-id/pictures",
                ),
                h.server.writes(),
            )
        }

    @Test
    fun `a refused save shows the API's detail and can be tried again`() = runTest {
        val h = Harness(this)
        h.server.problem =
            """{"title":"Bad Request","status":400,"detail":"Ingredient 1 is a cycle."}"""
        h.model.open(null)
        h.model.awaitEditing()
        fill(h.model)
        h.model.save()
        val refused =
            h.model.state.first { (it as RecipeFormState.Editing).errors.isNotEmpty() }
                as RecipeFormState.Editing

        assertEquals(listOf("Ingredient 1 is a cycle."), refused.errors)
        assertEquals(false, refused.saving)
        assertTrue(h.saved.value.isEmpty())
    }

    @Test
    fun `a new ingredient asks for its four values, then is saved, searchable, and linked`() =
        runTest {
            val h = Harness(this)
            h.model.open(null)
            h.model.awaitEditing()
            h.model.typeName(0, " dragon fruit ")
            h.model.startNewIngredient(0)
            assertEquals(
                RowIngredient(0, NewIngredientDraft("dragon fruit", basisForUnit(""))),
                h.editing().adding,
            )

            h.model.editNewIngredient { it.copy(calories = "60", proteinG = "1") }
            h.model.saveNewIngredient()
            assertEquals(
                "Fill in all four values, using 0 when there is none.",
                h.editing().adding?.draft?.error,
            )

            h.model.editNewIngredient { it.copy(carbsG = "13", fatG = "0") }
            h.model.saveNewIngredient()

            val form =
                h.model.state.first { (it as RecipeFormState.Editing).adding == null }
                    as RecipeFormState.Editing
            assertNull(form.adding)
            assertTrue(form.catalog.any { it.slug == "dragon-fruit-x1" })
            assertEquals(RowLink.Entry("dragon-fruit-x1"), form.draft.ingredients[0].link)
            val sent =
                h.server.seen.single {
                    it.method == HttpMethod.Post && it.path == "/api/v1/ingredients"
                }
            assertEquals("dragon fruit", sent.body!!["name"]!!.jsonPrimitive.content)
        }

    @Test
    fun `the new ingredient moves with its row, and goes when its row is removed`() = runTest {
        val h = Harness(this)
        h.model.open(null)
        h.model.awaitEditing()
        h.model.addIngredient()
        h.model.addIngredient()
        h.model.typeName(1, "dragon fruit")
        h.model.startNewIngredient(1)

        h.model.moveIngredient(1, 0)
        assertEquals(0, h.editing().adding?.row)
        assertEquals("dragon fruit", h.editing().draft.ingredients[0].name)

        h.model.moveIngredient(1, 0)
        assertEquals(1, h.editing().adding?.row)

        h.model.removeIngredient(0)
        assertEquals(0, h.editing().adding?.row)

        h.model.removeIngredient(0)
        assertNull(h.editing().adding)
    }

    @Test
    fun `closing drops the form`() = runTest {
        val h = Harness(this)
        h.model.open(null)
        h.model.awaitEditing()
        h.model.close()
        advanceUntilIdle()

        assertEquals(RecipeFormState.Closed, h.model.state.value)
    }
}

private fun fill(model: RecipeFormModel) {
    model.edit { it.copy(title = "Soup", description = "Warm.", steps = listOf("Simmer.")) }
    model.editRow(0) { it.copy(quantity = "1", unit = "l", name = "stock") }
}
