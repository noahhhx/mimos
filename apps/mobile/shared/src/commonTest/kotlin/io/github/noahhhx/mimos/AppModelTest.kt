package io.github.noahhhx.mimos

import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.instance.InstanceStore
import io.github.noahhhx.mimos.shell.Destination
import io.github.noahhhx.mimos.ui.MemoryThemeStore
import io.github.noahhhx.mimos.ui.ThemeModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope

private class NoInstance : InstanceStore {
    override fun load(): Instance? = null

    override fun save(instance: Instance) = Unit

    override fun clear() = Unit
}

private fun model(debug: Boolean, scope: TestScope) =
    AppModel(
        scope = scope,
        connect = ConnectModel(NoInstance()) { error("never connects") },
        theme = ThemeModel(MemoryThemeStore()),
        debug = debug,
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
}
