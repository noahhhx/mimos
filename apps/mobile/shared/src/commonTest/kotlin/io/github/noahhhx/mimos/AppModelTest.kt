package io.github.noahhhx.mimos

import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.instance.InstanceStore
import io.github.noahhhx.mimos.recipes.RecipeId
import io.github.noahhhx.mimos.recipes.RecipeRoute
import io.github.noahhhx.mimos.recipes.RecipeSort
import io.github.noahhhx.mimos.recipes.RecipeSortStore
import io.github.noahhhx.mimos.shell.Destination
import io.github.noahhhx.mimos.ui.MemoryThemeStore
import io.github.noahhhx.mimos.ui.ThemeModel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private class NoInstance : InstanceStore {
    override fun load(): Instance? = null

    override fun save(instance: Instance) = Unit

    override fun clear() = Unit
}

private class NoSort : RecipeSortStore {
    override fun load(): RecipeSort? = null

    override fun save(sort: RecipeSort) = Unit
}

private fun model(debug: Boolean, scope: TestScope) =
    AppModel(
        scope = scope,
        connect = ConnectModel(NoInstance()) { error("never connects") },
        theme = ThemeModel(MemoryThemeStore()),
        debug = debug,
        engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) },
        recipeSortStore = NoSort(),
        createSession = { error("no session") },
    )

class AppModelTest {
    @Test
    fun `the app is called Mimos`() {
        assertEquals("Mimos", APP_NAME)
    }

    @Test
    fun `a debug build opens and closes the gallery`() {
        val app = model(debug = true, TestScope())

        app.openGallery()
        assertTrue(app.gallery.value)
        app.closeGallery()
        assertFalse(app.gallery.value)
    }

    @Test
    fun `a release build never opens the gallery`() {
        val app = model(debug = false, TestScope())

        app.openGallery()

        assertFalse(app.gallery.value)
    }

    @Test
    fun `signing out sends the shell back to the kitchen`() {
        val app = model(debug = false, TestScope())
        app.shell.go(Destination.Log)

        app.signOut()

        assertEquals(Destination.Kitchen, app.shell.destination.value)
    }

    @Test
    fun `signing out sends the recipes tab back to a fresh list`() {
        val app = model(debug = false, TestScope())
        app.recipes.open(RecipeId("r1"))

        app.signOut()

        assertEquals(RecipeRoute.List, app.recipes.route.value)
    }

    @Test
    fun `the system back button steps through a recipe trail only on the recipes tab`() = runTest {
        val app = model(debug = false, TestScope())
        assertFalse(app.goBack())

        app.shell.go(Destination.Recipes)
        app.recipes.open(RecipeId("r1"))
        assertTrue(app.canGoBack.first())

        app.shell.go(Destination.Plan)
        assertFalse(app.canGoBack.first())
        assertFalse(app.goBack())

        app.shell.go(Destination.Recipes)
        assertTrue(app.goBack())
        assertEquals(RecipeRoute.List, app.recipes.route.value)
        assertFalse(app.canGoBack.first())
    }
}
