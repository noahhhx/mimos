package io.github.noahhhx.mimos.session

/** The Keycloak client the app signs in as (ADR-0029), not the web's `oidcClientId`. */
const val OIDC_CLIENT_ID = "mimos-mobile"

/** The redirect Keycloak sends the browser back to, for sign-in and for ending the session. */
const val OIDC_REDIRECT_URI = "io.github.noahhhx.mimos.oauth:/oauth2redirect"

enum class AuthorizeKind {
    SignIn,
    CreateAccount,
}

sealed interface AuthorizeResult {
    data class Granted(val tokens: Tokens) : AuthorizeResult

    data object Cancelled : AuthorizeResult

    data class Failed(val reason: String) : AuthorizeResult
}

sealed interface RefreshResult {
    data class Granted(val tokens: Tokens) : RefreshResult

    /** Keycloak refused the refresh token: the session ended or the token is unknown. */
    data object InvalidGrant : RefreshResult

    /** No answer, or one that says nothing about the token. The token may still be good. */
    data object Unavailable : RefreshResult
}

/** The platform's sign-in for one instance: its browser flow and its token calls. */
interface Authorizer {
    suspend fun authorize(kind: AuthorizeKind, theme: String): AuthorizeResult

    suspend fun refresh(refreshToken: String): RefreshResult

    /** Ends the Keycloak session in the browser. Best effort: it never throws. */
    suspend fun endSession(idToken: String?)
}
