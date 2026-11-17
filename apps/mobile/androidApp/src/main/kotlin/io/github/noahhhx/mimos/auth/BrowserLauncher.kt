package io.github.noahhhx.mimos.auth

import android.content.Intent
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Carries AppAuth's browser intents between the sign-in, which lives as long as the app's models,
 * and the activity, which is recreated when the phone rotates. The activity starts each [Request]
 * once and hands the browser's answer back to [deliver], whichever activity instance is current.
 */
class BrowserLauncher {
    class Request(val intent: Intent) {
        internal val result = CompletableDeferred<Intent?>()
        private val claimed = AtomicBoolean(false)

        /**
         * True for the first caller only, so a recreated activity does not open a second browser.
         */
        fun claim(): Boolean = claimed.compareAndSet(false, true)
    }

    private val mutableRequest = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = mutableRequest.asStateFlow()
    private val oneAtATime = Mutex()

    /** Opens [intent] and waits for the browser flow; null when the person backed out of it. */
    suspend fun launch(intent: Intent): Intent? = oneAtATime.withLock {
        val pending = Request(intent)
        mutableRequest.value = pending
        try {
            pending.result.await()
        } finally {
            mutableRequest.value = null
        }
    }

    fun deliver(data: Intent?) {
        mutableRequest.value?.result?.complete(data)
    }
}
