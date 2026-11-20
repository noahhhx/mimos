package io.github.noahhhx.mimos.shopping

import io.github.noahhhx.mimos.api.models.ShoppingList
import io.github.noahhhx.mimos.client.MimosJson
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Text kept on the phone for the shopping list (ADR-0034). The lists and the pending ticks are
 * separate entries, so a list a newer app wrote, which this one cannot read, never costs a tick.
 */
interface ShoppingStore {
    enum class Entry {
        Lists,
        Ticks,
    }

    fun load(entry: Entry): String?

    /** Null removes the entry. */
    fun save(entry: Entry, value: String?)
}

/** The most weeks kept on the phone: the ones loaded last. */
const val STORED_WEEKS = 4

@Serializable
private data class StoredTick(val itemId: String, val monday: LocalDate, val checked: Boolean)

private val listsSerializer = ListSerializer(ShoppingList.serializer())
private val ticksSerializer = ListSerializer(StoredTick.serializer())

/** Oldest first, as they were loaded; anything unreadable is nothing stored. */
internal fun decodeLists(text: String?): Map<LocalDate, ShoppingList> =
    decodeOrNull(text) { MimosJson.decodeFromString(listsSerializer, it) }
        ?.associateBy { it.startDate }
        .orEmpty()

internal fun encodeLists(lists: Map<LocalDate, ShoppingList>): String =
    MimosJson.encodeToString(listsSerializer, lists.values.toList())

/** Restored ticks are [TickStatus.Unsent]: nothing has tried them since the app started. */
internal fun decodeTicks(text: String?): Map<String, Tick> =
    decodeOrNull(text) { MimosJson.decodeFromString(ticksSerializer, it) }
        ?.associate { it.itemId to Tick(it.monday, it.checked, TickStatus.Unsent) }
        .orEmpty()

internal fun encodeTicks(ticks: Map<String, Tick>): String =
    MimosJson.encodeToString(
        ticksSerializer,
        ticks.map { (id, tick) -> StoredTick(id, tick.monday, tick.checked) },
    )

private fun <T> decodeOrNull(text: String?, decode: (String) -> T): T? = text?.let {
    try {
        decode(it)
    } catch (_: IllegalArgumentException) {
        // SerializationException is one; a stored value this app cannot read is no value.
        null
    }
}
