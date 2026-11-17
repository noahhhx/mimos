package io.github.noahhhx.mimos.session

fun SignedOutNotice.message(): String =
    when (this) {
        SignedOutNotice.Expired -> "Your session ended. Sign in again to carry on."
        SignedOutNotice.Unreadable ->
            "The app could not read your saved sign-in, so it forgot it. Sign in again."
        is SignedOutNotice.SignInFailed -> "Sign-in did not finish. $reason"
    }
