package io.github.noahhhx.mimos.recipes.collections

import io.github.noahhhx.mimos.api.models.RecipeCollectionInput
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionDraftTest {
    @Test
    fun `tags are kept trimmed and lowercase, once each`() {
        val draft = CollectionDraft().addTag(" Soup ").addTag("soup").toggleTag("Quick")
        assertEquals(listOf("soup", "quick"), draft.tags)
        assertEquals(listOf("quick"), draft.toggleTag("SOUP").tags)
    }

    @Test
    fun `a blank or overlong tag is not added, nor one past the limit`() {
        assertEquals(emptyList(), CollectionDraft().addTag("  ").tags)
        assertEquals(emptyList(), CollectionDraft().addTag("x".repeat(MAX_TAG_LENGTH + 1)).tags)
        val full = (1..MAX_TAGS).fold(CollectionDraft()) { draft, n -> draft.addTag("t$n") }
        assertEquals(MAX_TAGS, full.addTag("one more").tags.size)
    }

    @Test
    fun `a recipe toggles in and out`() {
        val draft = CollectionDraft().toggleRecipe("a").toggleRecipe("b").toggleRecipe("a")
        assertEquals(listOf("b"), draft.recipeIds)
    }

    @Test
    fun `the name is required and sent trimmed`() {
        assertEquals(
            CheckedCollection.Invalid("Give the tab a name."),
            CollectionDraft(name = " ").check(),
        )
        assertEquals(
            CheckedCollection.Valid(RecipeCollectionInput("Soups", listOf("soup"), listOf("a"))),
            CollectionDraft(name = " Soups ", tags = listOf("soup"), recipeIds = listOf("a"))
                .check(),
        )
    }
}
