package io.github.noahhhx.mimos

import android.content.Context
import androidx.core.content.edit
import io.github.noahhhx.mimos.plan.PlanView
import io.github.noahhhx.mimos.plan.PlanViewStore

/** The plan's Mine or Everyone in plain preferences, as the web keeps it in `localStorage`. */
class SharedPreferencesPlanViewStore(context: Context) : PlanViewStore {
    private val preferences = context.getSharedPreferences("plan", Context.MODE_PRIVATE)

    override fun load(): PlanView? =
        preferences.getString(KEY, null)?.let { stored ->
            PlanView.entries.find { it.name == stored }
        }

    override fun save(view: PlanView) = preferences.edit { putString(KEY, view.name) }

    private companion object {
        const val KEY = "view"
    }
}
