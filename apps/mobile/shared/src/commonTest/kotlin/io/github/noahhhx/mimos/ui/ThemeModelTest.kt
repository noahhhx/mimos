package io.github.noahhhx.mimos.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeModelTest {
    @Test
    fun `a new install is light, whatever the system prefers`() {
        val model = ThemeModel(MemoryThemeStore())

        assertEquals(ThemeChoice.Light, model.state.value)
        assertEquals("light", model.authTheme)
    }

    @Test
    fun `a choice is kept for the next launch`() {
        val store = MemoryThemeStore()
        ThemeModel(store).choose(ThemeChoice.Dark)

        val relaunched = ThemeModel(store)

        assertEquals(ThemeChoice.Dark, relaunched.state.value)
        assertEquals("dark", relaunched.authTheme)
    }

    @Test
    fun `toggling flips between the two and stores each`() {
        val store = MemoryThemeStore()
        val model = ThemeModel(store)

        model.toggle()
        assertEquals(ThemeChoice.Dark, store.stored)
        model.toggle()
        assertEquals(ThemeChoice.Light, store.stored)
        assertEquals(ThemeChoice.Light, model.state.value)
    }

    @Test
    fun `the colours are the web's tokens for each theme`() {
        assertEquals(MimosColors.Light, ThemeChoice.Light.colors)
        assertEquals(MimosColors.Dark, ThemeChoice.Dark.colors)
    }

    @Test
    fun `an unknown stored key reads as light`() {
        assertEquals(ThemeChoice.Light, ThemeChoice.fromKey("sepia"))
        assertEquals(ThemeChoice.Light, ThemeChoice.fromKey(null))
        assertEquals(ThemeChoice.Dark, ThemeChoice.fromKey("dark"))
    }
}
