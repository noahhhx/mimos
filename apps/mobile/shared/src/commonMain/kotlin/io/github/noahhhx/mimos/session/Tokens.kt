package io.github.noahhhx.mimos.session

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What a sign-in or a refresh hands back. Only the access token is short-lived. */
data class Tokens(
    val accessToken: String,
    val accessExpiresAt: Instant,
    val refreshToken: String,
    val idToken: String?,
) {
    fun needsRefresh(now: Instant): Boolean = now >= accessExpiresAt - REFRESH_LEEWAY
}

private val REFRESH_LEEWAY = 30.seconds

/**
 * What the store holds for a person: only what is needed to sign in again, never the access token.
 */
sealed interface StoredSession {
    data object None : StoredSession

    /** Something is stored but cannot be opened, such as a backup restored without its key. */
    data object Unreadable : StoredSession

    data class Present(val refreshToken: String, val idToken: String?) : StoredSession
}

/** Keeps the refresh token between launches, encrypted where the platform can. */
interface TokenStore {
    suspend fun load(): StoredSession

    suspend fun save(session: StoredSession.Present)

    suspend fun clear()
}
