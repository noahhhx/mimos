package io.github.noahhhx.mimos.session

sealed interface SignedOutNotice {
    /** Keycloak ended the session (invalid_grant), or it ran out. */
    data object Expired : SignedOutNotice

    /** The stored token would not open, so the person signs in again. */
    data object Unreadable : SignedOutNotice

    data class SignInFailed(val reason: String) : SignedOutNotice
}

sealed interface SessionState {
    data class SignedOut(val notice: SignedOutNotice? = null) : SessionState

    data object SigningIn : SessionState

    /** Restoring the session at launch: the stored refresh token is being exchanged. */
    data object Refreshing : SessionState

    /** A stored session exists but the server did not answer. [SessionModel.retry] tries again. */
    data object Unreachable : SessionState

    data class SignedIn(val tokens: Tokens) : SessionState
}
