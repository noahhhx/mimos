package io.github.noahhhx.mimos.recipes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val focaccia = RecipeId("focaccia")
private val sandwich = RecipeId("sandwich")

class CookBookTest {
    @Test
    fun `ticks toggle on and off and are kept per recipe`() {
        val book = CookBook()
        book.visit(focaccia, "Focaccia")

        book.toggleIngredient(focaccia, 1)
        book.toggleIngredient(focaccia, 3)
        book.toggleIngredient(focaccia, 1)
        book.toggleStep(focaccia, 0)
        book.toggleStep(sandwich, 2)

        assertEquals(CookState("Focaccia", setOf(3), setOf(0)), book.read(focaccia, 5, 5))
        assertEquals(setOf(2), book.read(sandwich, 5, 5).steps)
    }

    @Test
    fun `ticks past the end of the recipe are dropped when read`() {
        val book = CookBook()
        book.toggleIngredient(focaccia, 0)
        book.toggleIngredient(focaccia, 4)
        book.toggleStep(focaccia, 2)
        book.toggleStep(focaccia, 7)

        val read = book.read(focaccia, ingredientCount = 3, stepCount = 3)

        assertEquals(setOf(0), read.ingredients)
        assertEquals(setOf(2), read.steps)
    }

    @Test
    fun `visiting names a recipe and keeps its ticks`() {
        val book = CookBook()
        book.toggleStep(focaccia, 1)

        book.visit(focaccia, "Focaccia")

        assertEquals("Focaccia", book.title(focaccia))
        assertEquals(setOf(1), book.read(focaccia, 0, 3).steps)
        assertNull(book.title(sandwich))
    }

    @Test
    fun `clearing forgets every recipe, and forgetting forgets one`() {
        val book = CookBook()
        book.visit(focaccia, "Focaccia")
        book.visit(sandwich, "Sandwich")

        book.forget(focaccia)
        assertNull(book.title(focaccia))
        assertEquals("Sandwich", book.title(sandwich))

        book.clear()
        assertNull(book.title(sandwich))
        assertEquals(CookState(), book.read(sandwich, 3, 3))
    }
}
