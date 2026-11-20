package io.github.noahhhx.mimos

import android.content.Context
import androidx.core.content.edit
import io.github.noahhhx.mimos.recipes.RecipeSort
import io.github.noahhhx.mimos.recipes.RecipeSortStore

/** The recipes list's sort in plain preferences, as the web keeps it in `localStorage`. */
class SharedPreferencesRecipeSortStore(context: Context) : RecipeSortStore {
    private val preferences = context.getSharedPreferences("recipes", Context.MODE_PRIVATE)

    override fun load(): RecipeSort? =
        preferences.getString(KEY, null)?.let { RecipeSort.parse(it) }

    override fun save(sort: RecipeSort) = preferences.edit { putString(KEY, sort.key) }

    private companion object {
        const val KEY = "sort"
    }
}
