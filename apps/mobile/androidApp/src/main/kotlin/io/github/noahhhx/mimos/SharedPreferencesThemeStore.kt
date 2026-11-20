package io.github.noahhhx.mimos

import android.content.Context
import androidx.core.content.edit
import io.github.noahhhx.mimos.ui.ThemeChoice
import io.github.noahhhx.mimos.ui.ThemeStore

/** The chosen theme in plain preferences. Read before the first frame, so it is not DataStore. */
class SharedPreferencesThemeStore(context: Context) : ThemeStore {
    private val preferences = context.getSharedPreferences("theme", Context.MODE_PRIVATE)

    override fun load(): ThemeChoice? =
        preferences.getString(KEY, null)?.let { ThemeChoice.fromKey(it) }

    override fun save(choice: ThemeChoice) = preferences.edit { putString(KEY, choice.key) }

    private companion object {
        const val KEY = "theme"
    }
}
