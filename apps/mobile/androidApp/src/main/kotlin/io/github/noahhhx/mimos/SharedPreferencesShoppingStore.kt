package io.github.noahhhx.mimos

import android.content.Context
import androidx.core.content.edit
import io.github.noahhhx.mimos.shopping.ShoppingStore

/**
 * The stored shopping lists and pending ticks (ADR-0034), left out of backups and device transfers
 * with the session they belong to.
 */
class SharedPreferencesShoppingStore(context: Context) : ShoppingStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(entry: ShoppingStore.Entry): String? = preferences.getString(entry.key, null)

    override fun save(entry: ShoppingStore.Entry, value: String?) = preferences.edit {
        if (value == null) remove(entry.key) else putString(entry.key, value)
    }

    private val ShoppingStore.Entry.key: String
        get() =
            when (this) {
                ShoppingStore.Entry.Lists -> "lists"
                ShoppingStore.Entry.Ticks -> "ticks"
            }

    companion object {
        /** Named in `backup_rules.xml` and `data_extraction_rules.xml`. */
        const val FILE = "shopping"
    }
}
