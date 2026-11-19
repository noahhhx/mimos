package io.github.noahhhx.mimos.shopping

import io.github.noahhhx.mimos.api.models.ShoppingListItem
import io.github.noahhhx.mimos.api.models.ShoppingListItemInput
import io.github.noahhhx.mimos.plan.dayLabel
import io.github.noahhhx.mimos.recipes.form.UNITS
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Store order: the aisles a shopper walks, in the order they meet them. Unknown ones go last. */
private val AISLE_ORDER =
    listOf("Produce", "Meat & Seafood", "Dairy & Eggs", "Bakery", "Frozen", "Pantry", "Other")

data class Aisle(val category: String, val items: List<ShoppingListItem>) {
    val done: Boolean
        get() = items.all { it.checked }
}

/**
 * The list as the shop sees it, as the web's `aislesToShop`: grouped by aisle, what is still to buy
 * above what is in the basket. Aisles with something left come first, in store order; aisles
 * already done follow, in the same order. Within each part the list's own order is kept.
 */
fun aislesToShop(items: List<ShoppingListItem>): List<Aisle> {
    fun rank(category: String) =
        AISLE_ORDER.indexOf(category).takeIf { it >= 0 } ?: AISLE_ORDER.size
    return items
        .groupBy { it.category }
        .map { (category, aisleItems) ->
            val (left, ticked) = aisleItems.partition { !it.checked }
            Aisle(category, left + ticked)
        }
        .sortedWith(compareBy<Aisle> { it.done }.thenBy { rank(it.category) })
}

/** The units the add-an-item form offers: a recipe's metric units, with "" for pieces. */
val ADD_ITEM_UNITS: List<String> = UNITS

/** The add-an-item form as typed. */
data class AddItemFields(val name: String = "", val quantity: String = "", val unit: String = "")

sealed interface AddedItem {
    data class Valid(val input: ShoppingListItemInput) : AddedItem

    data class Invalid(val error: String) : AddedItem
}

/** The most a hand-added item can be, as the API allows. */
private const val MAX_QUANTITY = 10_000

private val DECIMAL = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

/**
 * The form's fields as an item to add, or what to fix first, as the web's `addedItem`. Only a name
 * is needed; an amount is optional, and a unit needs an amount.
 */
fun addedItem(fields: AddItemFields): AddedItem {
    val name = fields.name.trim()
    if (name.isEmpty()) return AddedItem.Invalid("Say what to buy.")
    if (name.length > 200) return AddedItem.Invalid("Keep the name to 200 characters.")
    val unit = fields.unit.trim()
    if (unit !in ADD_ITEM_UNITS) return AddedItem.Invalid("Choose a unit from the list.")
    val typed = fields.quantity.trim()
    if (typed.isEmpty()) {
        return if (unit.isEmpty()) AddedItem.Valid(ShoppingListItemInput(name))
        else AddedItem.Invalid("Give an amount for the unit, or choose pieces.")
    }
    // What JavaScript's Number reads: Kotlin would also take "2f" and "0x1p3".
    val quantity = typed.takeIf { DECIMAL.matches(it) }?.toDoubleOrNull()
    if (quantity == null || !quantity.isFinite() || quantity <= 0 || quantity > MAX_QUANTITY) {
        return AddedItem.Invalid("The amount must be more than 0 and at most $MAX_QUANTITY.")
    }
    return AddedItem.Valid(ShoppingListItemInput(name, quantity, unit.ifEmpty { null }))
}

/** "Generated Thu 19 Nov at 09:05 · regenerating keeps checked-off and added items." */
fun generatedText(at: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = at.toLocalDateTime(zone)
    val clock =
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    return "Generated ${dayLabel(time.date)} at $clock · regenerating keeps checked-off and added items."
}
