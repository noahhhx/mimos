package io.github.noahhhx.mimos.recipes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val a = RecipeId("a")
private val b = RecipeId("b")
private val c = RecipeId("c")

class RecipeRouteTest {
    @Test
    fun `opening from the list starts a trail, and following adds to it`() {
        val opened = RecipeRoute.List.follow(a)
        assertEquals(RecipeRoute.Recipe(emptyList(), a), opened)

        val followed = opened.follow(b).follow(c)
        assertEquals(RecipeRoute.Recipe(listOf(a, b), c), followed)
    }

    @Test
    fun `back pops one recipe at a time, then the list, then nothing`() {
        var route: RecipeRoute? = RecipeRoute.Recipe(listOf(a, b), c)

        route = route?.back()
        assertEquals(RecipeRoute.Recipe(listOf(a), b), route)
        route = route?.back()
        assertEquals(RecipeRoute.Recipe(emptyList(), a), route)
        route = route?.back()
        assertEquals(RecipeRoute.List, route)
        assertNull(route?.back())
    }

    @Test
    fun `following the recipe on screen changes nothing, and one on the trail returns to it`() {
        val here = RecipeRoute.Recipe(listOf(a, b), c)

        assertEquals(here, here.follow(c))
        assertEquals(RecipeRoute.Recipe(listOf(a), b), here.follow(b))
        assertEquals(RecipeRoute.Recipe(emptyList(), a), here.follow(a))
    }
}
