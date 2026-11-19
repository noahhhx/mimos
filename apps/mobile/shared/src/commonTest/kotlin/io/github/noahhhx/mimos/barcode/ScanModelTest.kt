package io.github.noahhhx.mimos.barcode

import io.github.noahhhx.mimos.api.apis.FoodProductsApi
import io.github.noahhhx.mimos.api.models.FoodProduct
import io.github.noahhhx.mimos.client.MimosHttp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private const val API = "https://api.mimos.example.com"
private const val NUTELLA = "3017620422003"

/** Open Food Facts through the API: Nutella is known, and [down] makes every lookup a 502. */
private class Products {
    var down = false
    val asked = mutableListOf<String>()
    val engine = MockEngine { request ->
        val barcode = request.url.encodedPath.substringAfterLast('/')
        asked += barcode
        when {
            down -> respondError(HttpStatusCode.BadGateway)
            barcode == NUTELLA ->
                respond(
                    """{"barcode":"$NUTELLA","name":"Nutella","basis":"PER_100_G",
                        "nutrition":{"calories":539}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            else -> respondError(HttpStatusCode.NotFound)
        }
    }
}

private fun TestScope.scanner(products: Products, found: MutableList<FoodProduct>) =
    ScanModel(
        this,
        { MimosHttp(products.engine, API).api(::FoodProductsApi) },
        onFound = { found += it },
    )

class ScanModelTest {
    @Test
    fun `a typed barcode that is found closes the sheet and hands over the product`() = runTest {
        val products = Products()
        val found = mutableListOf<FoodProduct>()
        val model = scanner(products, found)
        model.open()

        model.type("3017 6204 22003")
        model.lookUpTyped()

        model.scan.first { it == null }
        assertEquals(listOf(NUTELLA), products.asked)
        assertEquals(listOf("Nutella"), found.map { it.name })
    }

    @Test
    fun `nothing is looked up until the number is a barcode`() = runTest {
        val products = Products()
        val model = scanner(products, mutableListOf())
        model.open()

        model.type("1234567")
        model.lookUpTyped()

        assertTrue(products.asked.isEmpty())
        assertEquals(Lookup.Idle, model.scan.value?.lookup)
    }

    @Test
    fun `an unknown barcode and Open Food Facts being down each keep the sheet open with their message`() =
        runTest {
            val products = Products()
            val model = scanner(products, mutableListOf())
            model.open()

            model.type("4006381333931")
            model.lookUpTyped()
            assertEquals(
                Lookup.Failed(lookupFailure(404)),
                model.scan.first { it?.lookup is Lookup.Failed }?.lookup,
            )

            products.down = true
            model.type(NUTELLA)
            assertEquals(Lookup.Idle, model.scan.value?.lookup)
            model.lookUpTyped()
            assertEquals(
                Lookup.Failed(lookupFailure(502)),
                model.scan.first { it?.lookup is Lookup.Failed }?.lookup,
            )
        }

    @Test
    fun `the camera stops at a read, which is looked up, and scans again after a miss`() = runTest {
        val products = Products()
        val model = scanner(products, mutableListOf())
        model.open()

        model.detected("4006381333931")
        model.detected(NUTELLA)

        val missed = model.scan.first { it?.lookup is Lookup.Failed }!!
        assertEquals("4006381333931", missed.typed)
        assertEquals(false, missed.cameraLooking)
        assertEquals(listOf("4006381333931"), products.asked)

        model.scanAgain()
        assertEquals(true, model.scan.value?.cameraLooking)
        assertEquals(Lookup.Idle, model.scan.value?.lookup)
    }
}
