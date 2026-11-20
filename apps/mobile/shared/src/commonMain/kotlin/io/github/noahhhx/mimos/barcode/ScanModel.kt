package io.github.noahhhx.mimos.barcode

import io.github.noahhhx.mimos.api.apis.FoodProductsApi
import io.github.noahhhx.mimos.api.models.FoodProduct
import io.github.noahhhx.mimos.client.ApiProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Lookup {
    data object Idle : Lookup

    data object Looking : Lookup

    data class Failed(val message: String) : Lookup
}

/**
 * The scan sheet while it is open: the number typed or read, the lookup, and whether the camera
 * still looks (it stops at a read, so the read can be looked up).
 */
data class Scan(
    val typed: String = "",
    val lookup: Lookup = Lookup.Idle,
    val cameraLooking: Boolean = true,
) {
    val barcode: String?
        get() = parseBarcode(typed)
}

/**
 * "Scan a barcode", as the web's dialog: finds a packaged food in Open Food Facts (ADR-0025) by its
 * barcode, read by the camera or typed. A found product goes to [onFound] and closes the sheet; an
 * unknown one, or Open Food Facts not answering, keeps it open with its message.
 */
class ScanModel(
    private val scope: CoroutineScope,
    private val api: () -> FoodProductsApi,
    private val onFound: (FoodProduct) -> Unit,
) {
    private val mutableScan = MutableStateFlow<Scan?>(null)

    /** Null while the sheet is closed. */
    val scan: StateFlow<Scan?> = mutableScan.asStateFlow()

    private var job: Job? = null

    fun open() {
        mutableScan.value = Scan()
    }

    fun close() {
        job?.cancel()
        mutableScan.value = null
    }

    fun type(text: String) = mutableScan.update {
        it?.copy(typed = text, lookup = if (it.lookup is Lookup.Looking) it.lookup else Lookup.Idle)
    }

    fun lookUpTyped() {
        lookUp(scan.value?.barcode ?: return)
    }

    /** The camera read [code]: it stops looking, and the code is looked up as if typed. */
    fun detected(code: String) {
        val current = scan.value?.takeIf { it.cameraLooking } ?: return
        mutableScan.value = current.copy(typed = code, cameraLooking = false)
        parseBarcode(code)?.let(::lookUp)
    }

    fun scanAgain() = mutableScan.update {
        it?.copy(lookup = Lookup.Idle, cameraLooking = true)
    }

    private fun lookUp(barcode: String) {
        if (scan.value?.lookup == Lookup.Looking) return
        mutableScan.update { it?.copy(lookup = Lookup.Looking) }
        job = scope.launch {
            val failure =
                try {
                    val product = api().lookUpFoodProduct(barcode).body()
                    close()
                    onFound(product)
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (problem: ApiProblem) {
                    lookupFailure(problem.status)
                } catch (_: Exception) {
                    lookupFailure(null)
                }
            mutableScan.update { it?.copy(lookup = Lookup.Failed(failure)) }
        }
    }
}
