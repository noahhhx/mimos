package io.github.noahhhx.mimos.recipes

import io.github.noahhhx.mimos.api.models.RecipeCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private fun tab(id: String, isDefault: Boolean = false) =
    RecipeCollection(
        id = id,
        name = id,
        tags = emptyList(),
        recipeIds = emptyList(),
        isDefault = isDefault,
        recipeCount = 0,
        createdAt = Instant.parse("2026-11-01T10:00:00Z"),
    )

class RecipeTabTest {
    @Test
    fun `without tabs Recipes opens on My recipes`() {
        assertEquals(RecipeTab.Mine, landingTab(emptyList()))
    }

    @Test
    fun `with tabs and no default Recipes opens on the gallery`() {
        assertEquals(RecipeTab.Gallery, landingTab(listOf(tab("a"), tab("b"))))
    }

    @Test
    fun `the caller's default tab wins over the gallery`() {
        assertEquals(
            RecipeTab.Collection("b"),
            landingTab(listOf(tab("a"), tab("b", isDefault = true))),
        )
    }

    @Test
    fun `a chosen tab stays while it exists`() {
        val tabs = listOf(tab("a"), tab("b", isDefault = true))
        assertEquals(RecipeTab.Library, shownTab(RecipeTab.Library, tabs))
        assertEquals(RecipeTab.Collection("a"), shownTab(RecipeTab.Collection("a"), tabs))
        assertEquals(RecipeTab.Gallery, shownTab(RecipeTab.Gallery, tabs))
        assertEquals(RecipeTab.Collection("b"), shownTab(null, tabs))
    }

    @Test
    fun `a chosen tab that is gone falls back to the landing tab`() {
        assertEquals(RecipeTab.Gallery, shownTab(RecipeTab.Collection("gone"), listOf(tab("a"))))
        assertEquals(RecipeTab.Mine, shownTab(RecipeTab.Collection("gone"), emptyList()))
        assertEquals(RecipeTab.Mine, shownTab(RecipeTab.Gallery, emptyList()))
    }

    @Test
    fun `without tabs the bar is today's two`() {
        assertEquals(listOf(RecipeTab.Mine, RecipeTab.Library), tabBar(emptyList()))
    }

    @Test
    fun `with tabs the bar leads with the gallery and ends with each tab in order`() {
        assertEquals(
            listOf(
                RecipeTab.Gallery,
                RecipeTab.Mine,
                RecipeTab.Library,
                RecipeTab.Collection("a"),
                RecipeTab.Collection("b"),
            ),
            tabBar(listOf(tab("a"), tab("b"))),
        )
    }
}
