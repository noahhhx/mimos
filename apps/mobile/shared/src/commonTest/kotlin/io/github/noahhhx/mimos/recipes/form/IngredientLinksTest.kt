package io.github.noahhhx.mimos.recipes.form

import io.github.noahhhx.mimos.api.models.IngredientLineStatus
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

/** A link as the cases write it: `{"entry": slug}` or `{"recipe": id}`. */
@Serializable private class LinkSketch(val entry: String? = null, val recipe: String? = null)

@Serializable private class RecipeSearchCase(val query: String, val expected: List<String>)

@Serializable
private class DescribeRecipeCase(val recipe: String, val isLibrary: Boolean, val expected: String)

@Serializable
private class PickEntryCase(
    val name: String,
    val row: RowSketch,
    val entry: String,
    val expected: RowSketch,
)

@Serializable
private class PickRecipeCase(
    val name: String,
    val row: RowSketch,
    val recipe: String,
    val expected: RowSketch,
)

@Serializable private class DontCountCase(val row: RowSketch, val expected: RowSketch)

@Serializable
private class RetypeCase(
    val name: String,
    val row: RowSketch,
    val typed: String,
    val recipesLoaded: Boolean = true,
    val expected: RowSketch,
)

@Serializable private class LinkOfCase(val row: RowSketch, val expected: LinkSketch?)

@Serializable private class DescribeLinkCase(val link: LinkSketch, val expected: String)

@Serializable
private class LineHintCase(
    val status: IngredientLineStatus?,
    val link: LinkSketch?,
    val expected: String?,
)

@Serializable
private class LinkCases(
    val catalog: List<EntrySketch>,
    val recipes: List<RecipeSketch>,
    val searchRecipesCases: List<RecipeSearchCase>,
    val describeRecipeCases: List<DescribeRecipeCase>,
    val pickEntryCases: List<PickEntryCase>,
    val pickRecipeCases: List<PickRecipeCase>,
    val dontCountCases: List<DontCountCase>,
    val retypeCases: List<RetypeCase>,
    val linkOfCases: List<LinkOfCase>,
    val describeLinkCases: List<DescribeLinkCase>,
    val lineHintCases: List<LineHintCase>,
)

private val cases = MimosJson.decodeFromString<LinkCases>(TestCases.read("ingredient-links.json"))
private val catalog = cases.catalog.map { it.toEntry() }
private val recipes = cases.recipes.map { it.toSummary() }
private val bySlug = catalog.associateBy { it.slug }
private val byId = recipes.associateBy { it.id }

private fun LinkSketch.toLink(): LineLink =
    entry?.let { LineLink.Entry(bySlug.getValue(it)) }
        ?: LineLink.Recipe(byId.getValue(recipe ?: error("a link names an entry or a recipe")))

/** The cases are the web's too (`libraries/test-cases`). */
class IngredientLinksTest {
    @Test
    fun `every shared recipe search and description is the web's`() {
        assertTrue(cases.searchRecipesCases.isNotEmpty())
        for (case in cases.searchRecipesCases) {
            assertEquals(
                case.expected,
                searchRecipes(case.query, recipes).map { it.id },
                case.query,
            )
        }
        for (case in cases.describeRecipeCases) {
            val recipe = cases.recipes.single { it.id == case.recipe }.toSummary(case.isLibrary)
            assertEquals(case.expected, describeRecipe(recipe), case.recipe)
        }
    }

    @Test
    fun `every shared pick links the row as the web's does`() {
        for (case in cases.pickEntryCases) {
            assertEquals(
                case.expected.toRow(),
                pickEntry(case.row.toRow(), bySlug.getValue(case.entry)),
                case.name,
            )
        }
        for (case in cases.pickRecipeCases) {
            assertEquals(
                case.expected.toRow(),
                pickRecipe(case.row.toRow(), byId.getValue(case.recipe)),
                case.name,
            )
        }
        for (case in cases.dontCountCases) {
            assertEquals(case.expected.toRow(), dontCount(case.row.toRow()), case.row.name)
        }
    }

    @Test
    fun `every shared retype keeps, drops, or finds a link as the web's does`() {
        for (case in cases.retypeCases) {
            assertEquals(
                case.expected.toRow(),
                retype(
                    case.row.toRow(),
                    case.typed,
                    catalog,
                    bySlug,
                    if (case.recipesLoaded) byId else emptyMap(),
                ),
                case.name,
            )
        }
    }

    @Test
    fun `every shared link is found and described as on the web`() {
        for (case in cases.linkOfCases) {
            assertEquals(
                case.expected?.toLink(),
                linkOf(case.row.toRow(), bySlug, byId),
                "${case.row}",
            )
        }
        for (case in cases.describeLinkCases) {
            assertEquals(case.expected, describeLink(case.link.toLink()), case.expected)
        }
        for (case in cases.lineHintCases) {
            assertEquals(
                case.expected,
                lineHint(case.status, case.link?.toLink()),
                "${case.status} ${case.link?.entry ?: case.link?.recipe}",
            )
        }
    }
}
