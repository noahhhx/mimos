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

/**
 * The list as the phone shows it in the shop: [aisles] hold only what is still to buy, and every
 * ticked item gathers in one [basket] at the end, in the order [aislesToShop] gives them. An aisle
 * with nothing left to buy is not in [aisles].
 */
data class BasketView(val aisles: List<Aisle>, val basket: List<ShoppingListItem>) {
    val total: Int
        get() = aisles.sumOf { it.items.size } + basket.size
}

fun basketView(items: List<ShoppingListItem>): BasketView {
    val inAisles = aislesToShop(items)
    return BasketView(
        aisles =
            inAisles.mapNotNull { aisle ->
                aisle.items
                    .filterNot { it.checked }
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        Aisle(aisle.category, it)
                    }
            },
        basket = inAisles.flatMap { aisle -> aisle.items.filter { it.checked } },
    )
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

/** The banner above the list while something waits or the list is a stored copy (ADR-0034). */
data class SendingNotice(val text: String, val action: String? = null)

/**
 * What the banner says. With no network the phone does not try, so it counts every tick that waits;
 * with one, only the ticks a try did not get through. [copy] is the shown list's, if any.
 */
fun sendingNotice(online: Boolean, ticks: Collection<Tick>, copy: ListCopy?): SendingNotice? {
    val stored = copy == ListCopy.Stored
    val asLastLoaded = "This is the list as it was last loaded."
    if (!online) {
        val waiting =
            if (ticks.isEmpty()) "Ticks wait on this phone until they can be sent."
            else "${tickCount(ticks.size)} waiting to send."
        return SendingNotice(
            listOfNotNull("Offline.", waiting, asLastLoaded.takeIf { stored }).joinToString(" ")
        )
    }
    val unsent = ticks.count { it.status == TickStatus.Unsent }
    return when {
        unsent > 0 ->
            SendingNotice(
                listOfNotNull("${tickCount(unsent)} not sent yet.", asLastLoaded.takeIf { stored })
                    .joinToString(" "),
                action = "Send now",
            )
        stored -> SendingNotice("Mimos could not be reached. $asLastLoaded", action = "Try again")
        else -> null
    }
}

private fun tickCount(count: Int) = if (count == 1) "1 tick" else "$count ticks"

/** "Generated Thu 19 Nov at 09:05 · regenerating keeps checked-off and added items." */
fun generatedText(at: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = at.toLocalDateTime(zone)
    val clock =
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    return "Generated ${dayLabel(time.date)} at $clock · regenerating keeps checked-off and added items."
}
