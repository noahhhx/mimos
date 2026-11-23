package io.github.noahhhx.mimos.recipes.collections

import io.github.noahhhx.mimos.api.models.RecipeCollection
import io.github.noahhhx.mimos.api.models.RecipeCollectionInput
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.recipes.pictures.PreparedPicture

/** Mirrors RecipeCollectionService's limits; the API is the authority. */
const val MAX_NAME_LENGTH = 60
const val MAX_TAGS = 12
const val MAX_TAG_LENGTH = 30

/** A tab's cover as the form holds it. */
sealed interface Cover {
    data object None : Cover

    data class Saved(val pictureId: String) : Cover

    /** Picked and shrunk, not uploaded yet. */
    data class New(val prepared: PreparedPicture) : Cover
}

/** What the tab form edits. Tags are kept as the API stores them: trimmed and lowercase. */
data class CollectionDraft(
    val name: String = "",
    val tags: List<String> = emptyList(),
    val recipeIds: List<String> = emptyList(),
    val cover: Cover = Cover.None,
    val openByDefault: Boolean = false,
) {
    val tagsFull: Boolean
        get() = tags.size >= MAX_TAGS

    fun toggleRecipe(id: String): CollectionDraft =
        copy(recipeIds = if (id in recipeIds) recipeIds - id else recipeIds + id)

    /** Turns [tag] off, or on while there is room. */
    fun toggleTag(tag: String): CollectionDraft {
        val normal = normalTag(tag)
        return when {
            normal in tags -> copy(tags = tags - normal)
            normal.isEmpty() || tagsFull -> this
            else -> copy(tags = tags + normal)
        }
    }

    /** Adds a typed tag, unless it is blank, already on, too long, or there is no room. */
    fun addTag(typed: String): CollectionDraft {
        val normal = normalTag(typed)
        return if (normal in tags || normal.length > MAX_TAG_LENGTH) this else toggleTag(normal)
    }

    /** The fields the API takes, or what is wrong with them. */
    fun check(): CheckedCollection {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> CheckedCollection.Invalid("Give the tab a name.")
            trimmed.length > MAX_NAME_LENGTH ->
                CheckedCollection.Invalid("A tab's name is at most $MAX_NAME_LENGTH characters.")
            else -> CheckedCollection.Valid(RecipeCollectionInput(trimmed, tags, recipeIds))
        }
    }

    companion object {
        fun of(saved: RecipeCollection?): CollectionDraft =
            if (saved == null) CollectionDraft()
            else
                CollectionDraft(
                    name = saved.name,
                    tags = saved.tags,
                    recipeIds = saved.recipeIds,
                    cover = saved.pictureId?.let(Cover::Saved) ?: Cover.None,
                    openByDefault = saved.isDefault,
                )
    }
}

sealed interface CheckedCollection {
    data class Valid(val input: RecipeCollectionInput) : CheckedCollection

    data class Invalid(val message: String) : CheckedCollection
}

fun normalTag(tag: String): String = tag.trim().lowercase()

/**
 * Every tag the household's recipes and the library carry, as tabs match them (ignoring case), with
 * the tags [chosen] already holds that no recipe carries yet, in alphabetical order.
 */
fun tagChoices(recipes: List<RecipeSummary>, chosen: List<String>): List<String> =
    (recipes.flatMap { it.tags }.map(::normalTag).filter { it.isNotEmpty() } + chosen)
        .distinct()
        .sorted()

/** The recipes whose title holds [query], ignoring case; all of them for a blank one. */
fun findRecipes(recipes: List<RecipeSummary>, query: String): List<RecipeSummary> {
    val wanted = query.trim()
    return if (wanted.isEmpty()) recipes
    else recipes.filter { it.title.contains(wanted, ignoreCase = true) }
}
