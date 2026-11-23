package io.github.noahhhx.mimos.shopping

import io.github.noahhhx.mimos.api.models.ShoppingListItem
import io.github.noahhhx.mimos.api.models.ShoppingListItemInput
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

@Serializable
private class SketchedItem(val name: String, val category: String, val checked: Boolean = false)

@Serializable private class ExpectedAisle(val category: String, val names: List<String>)

@Serializable
private class AisleCase(
    val about: String,
    val items: List<SketchedItem>,
    val expected: List<ExpectedAisle>,
)

@Serializable private class Fields(val name: String, val quantity: String, val unit: String)

@Serializable
private class AddedItemCase(
    val fields: Fields,
    val input: ShoppingListItemInput? = null,
    val error: String? = null,
)

@Serializable private class LongName(val length: Int, val error: String)

@Serializable
private class ShoppingCases(
    val aisleCases: List<AisleCase>,
    val units: List<String>,
    val addedItemCases: List<AddedItemCase>,
    val longName: LongName,
)

private val cases = MimosJson.decodeFromString<ShoppingCases>(TestCases.read("shopping.json"))

class ShoppingRulesTest {
    @Test
    fun `the list reads by aisle as on the web`() {
        for (case in cases.aisleCases) {
            val items =
                case.items.map {
                    ShoppingListItem(it.name, it.name, it.category, it.checked, added = false)
                }
            val shape =
                aislesToShop(items).map { ExpectedAisle(it.category, it.items.map { i -> i.name }) }
            assertEquals(
                case.expected.map { it.category to it.names },
                shape.map { it.category to it.names },
                case.about,
            )
        }
    }

    private fun item(name: String, category: String, checked: Boolean = false) =
        ShoppingListItem(name, name, category, checked, added = false)

    private fun BasketView.names() =
        aisles.map { it.category to it.items.map { i -> i.name } } to basket.map { it.name }

    @Test
    fun `ticked items leave their aisle for the basket, and an aisle with nothing left goes`() {
        val view =
            basketView(
                listOf(
                    item("milk", "Dairy & Eggs", checked = true),
                    item("onion", "Produce"),
                    item("garlic", "Produce", checked = true),
                    item("salt", "Pantry"),
                    item("leeks", "Produce"),
                )
            )
        assertEquals(
            listOf("Produce" to listOf("onion", "leeks"), "Pantry" to listOf("salt")) to
                listOf("garlic", "milk"),
            view.names(),
        )
        assertEquals(5, view.total)
    }

    @Test
    fun `an unticked item goes back to its aisle, and nothing ticked leaves the basket empty`() {
        val view = basketView(listOf(item("milk", "Dairy & Eggs"), item("onion", "Produce")))
        assertEquals(
            listOf("Produce" to listOf("onion"), "Dairy & Eggs" to listOf("milk")) to
                emptyList<String>(),
            view.names(),
        )
    }

    @Test
    fun `with everything ticked, only the basket is left`() {
        val view =
            basketView(listOf(item("onion", "Produce", true), item("milk", "Dairy & Eggs", true)))
        assertEquals(
            emptyList<Pair<String, List<String>>>() to listOf("onion", "milk"),
            view.names(),
        )
        assertEquals(2, view.total)
    }

    @Test
    fun `the form offers the web's units`() {
        assertEquals(cases.units, ADD_ITEM_UNITS)
    }

    @Test
    fun `the form makes the item the web would, or says what to fix`() {
        for (case in cases.addedItemCases) {
            val fields = AddItemFields(case.fields.name, case.fields.quantity, case.fields.unit)
            val expected =
                case.input?.let { AddedItem.Valid(it) } ?: AddedItem.Invalid(case.error!!)
            assertEquals(expected, addedItem(fields), case.fields.name + "/" + case.fields.quantity)
        }
        assertEquals(
            AddedItem.Invalid(cases.longName.error),
            addedItem(AddItemFields("a".repeat(cases.longName.length))),
        )
    }

    @Test
    fun `when the list was generated reads in the phone's time`() {
        assertEquals(
            "Generated Thu 19 Nov at 09:05 · regenerating keeps checked-off and added items.",
            generatedText(Instant.parse("2026-11-19T08:05:00Z"), TimeZone.of("Europe/Paris")),
        )
    }

    private fun ticks(vararg statuses: TickStatus) = statuses.map {
        Tick(kotlinx.datetime.LocalDate(2026, 11, 16), true, it)
    }

    @Test
    fun `offline, the banner counts every tick that waits, one tick in the singular`() {
        assertEquals(
            SendingNotice("Offline. 1 tick waiting to send."),
            sendingNotice(false, ticks(TickStatus.Unsent), ListCopy.Server),
        )
        assertEquals(
            SendingNotice(
                "Offline. 2 ticks waiting to send. This is the list as it was last loaded."
            ),
            sendingNotice(false, ticks(TickStatus.Unsent, TickStatus.Unsent), ListCopy.Stored),
        )
        assertEquals(
            SendingNotice("Offline. Ticks wait on this phone until they can be sent."),
            sendingNotice(false, emptyList(), null),
        )
    }

    @Test
    fun `online, the banner offers to send what did not go, and says nothing while all goes`() {
        assertEquals(
            SendingNotice("2 ticks not sent yet.", action = "Send now"),
            sendingNotice(
                true,
                ticks(TickStatus.Unsent, TickStatus.Sending, TickStatus.Unsent),
                ListCopy.Server,
            ),
        )
        assertEquals(null, sendingNotice(true, ticks(TickStatus.Sending), ListCopy.Server))
        assertEquals(
            SendingNotice(
                "Mimos could not be reached. This is the list as it was last loaded.",
                action = "Try again",
            ),
            sendingNotice(true, emptyList(), ListCopy.Stored),
        )
    }
}
