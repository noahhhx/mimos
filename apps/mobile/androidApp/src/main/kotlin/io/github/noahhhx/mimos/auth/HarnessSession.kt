package io.github.noahhhx.mimos.auth

import android.content.Intent
import io.github.noahhhx.mimos.session.StoredSession

/**
 * A session the agent harness signed in for, so a Maestro flow starts signed in without driving the
 * browser (`harness mobile flow`; the `connect-and-sign-in` flow still signs in for real). The
 * harness runs the same Authorization Code + PKCE sign-in as the app, as `mimos-mobile`, and passes
 * the web address and the tokens as `launchApp` arguments. Only a debug or minified build reads
 * them (`dev_build`); the app then stores the tokens and connects, and restores the session as it
 * does at any launch.
 */
data class HarnessSession(val webAddress: String, val stored: StoredSession.Present) {
    companion object {
        const val EXTRA_WEB_ADDRESS = "harnessWebAddress"
        const val EXTRA_REFRESH_TOKEN = "harnessRefreshToken"
        const val EXTRA_ID_TOKEN = "harnessIdToken"

        fun from(intent: Intent): HarnessSession? {
            val webAddress = intent.getStringExtra(EXTRA_WEB_ADDRESS)?.takeIf { it.isNotBlank() }
            val refreshToken =
                intent.getStringExtra(EXTRA_REFRESH_TOKEN)?.takeIf { it.isNotBlank() }
            if (webAddress == null || refreshToken == null) return null
            return HarnessSession(
                webAddress,
                StoredSession.Present(refreshToken, intent.getStringExtra(EXTRA_ID_TOKEN)),
            )
        }
    }
}
