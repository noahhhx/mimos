package io.github.noahhhx.mimos

import android.content.Context
import androidx.core.content.edit
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.instance.InstanceStore
import io.github.noahhhx.mimos.instance.decodeInstance
import io.github.noahhhx.mimos.instance.encode

/** The connected instance in plain preferences: addresses and a version, nothing secret. */
class SharedPreferencesInstanceStore(context: Context) : InstanceStore {
    private val preferences = context.getSharedPreferences("instance", Context.MODE_PRIVATE)

    override fun load(): Instance? = preferences.getString(KEY, null)?.let(::decodeInstance)

    override fun save(instance: Instance) = preferences.edit { putString(KEY, instance.encode()) }

    override fun clear() = preferences.edit { remove(KEY) }

    private companion object {
        const val KEY = "instance"
    }
}
