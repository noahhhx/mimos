package io.github.noahhhx.mimos.recipes.form

import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

@Serializable
private class CatalogSearchCase(val name: String, val query: String, val expected: List<String>)

@Serializable private class SuggestCase(val name: String, val expected: String?)

@Serializable private class MentionsCase(val name: String, val entry: String, val expected: Boolean)

@Serializable
private class ContainsCase(val text: String, val phrase: String, val expected: Boolean)

@Serializable private class EntryTextCase(val entry: String, val expected: String)

@Serializable private class BasisCase(val unit: String, val expected: NutritionBasis)

@Serializable
private class MatchCases(
    val catalog: List<EntrySketch>,
    val searchCases: List<CatalogSearchCase>,
    val suggestCases: List<SuggestCase>,
    val mentionsCases: List<MentionsCase>,
    val containsCases: List<ContainsCase>,
    val lineNameCases: List<EntryTextCase>,
    val describeEntryCases: List<EntryTextCase>,
    val basisCases: List<BasisCase>,
)

private val cases = MimosJson.decodeFromString<MatchCases>(TestCases.read("catalog-match.json"))
private val catalog = cases.catalog.map { it.toEntry() }

private fun entry(slug: String) = catalog.single { it.slug == slug }

/** The cases are the web's too (`libraries/test-cases`). */
class CatalogMatchTest {
    @Test
    fun `every shared search finds what the web's does, in its order`() {
        assertTrue(cases.searchCases.isNotEmpty())
        for (case in cases.searchCases) {
            assertEquals(
                case.expected,
                searchCatalog(case.query, catalog).map { it.slug },
                case.name,
            )
        }
    }

    @Test
    fun `a search finds at most eight`() {
        val many = (0 until 10).map { catalog[0].copy(slug = "s$it", name = "Salt $it") }
        assertEquals(8, searchCatalog("salt", many).size)
    }

    @Test
    fun `every shared line name suggests the web's entry`() {
        for (case in cases.suggestCases) {
            assertEquals(case.expected, suggestCatalogSlug(case.name, catalog), case.name)
        }
    }

    @Test
    fun `every shared name names its entry, or not, as on the web`() {
        for (case in cases.mentionsCases) {
            assertEquals(case.expected, mentions(case.name, entry(case.entry)), case.name)
        }
        for (case in cases.containsCases) {
            assertEquals(
                case.expected,
                containsAtWordStart(case.text, case.phrase),
                "${case.text} / ${case.phrase}",
            )
        }
    }

    @Test
    fun `every shared entry reads as on the web`() {
        for (case in cases.lineNameCases) {
            assertEquals(case.expected, lineNameOf(entry(case.entry)), case.entry)
        }
        for (case in cases.describeEntryCases) {
            assertEquals(case.expected, describeEntry(entry(case.entry)), case.entry)
        }
        for (case in cases.basisCases) {
            assertEquals(case.expected, basisForUnit(case.unit), case.unit)
        }
    }
}
