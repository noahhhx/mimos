package io.github.noahhhx.mimos.session

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.instance.SignupPolicy
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.EntryPage
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.Text

/**
 * What the person sees for the session's state. Signed in, the app itself ([signedIn]) takes over
 * the screen; every other state is a page of its own.
 */
@Composable
fun SessionScreen(
    model: SessionModel,
    instance: Instance,
    brandMark: Painter,
    onSignOut: () -> Unit,
    onChangeInstance: () -> Unit,
    signedIn: @Composable () -> Unit,
) {
    val state by model.state.collectAsState()
    val origin = instance.config.webUrl.origin
    val fill = Modifier.fillMaxWidth()
    when (val current = state) {
        is SessionState.SignedIn -> signedIn()
        SessionState.Refreshing ->
            EntryPage(brandMark, "Opening Mimos") {
                Text("Opening $origin…", color = MimosTheme.colors.muted)
            }
        SessionState.SigningIn ->
            EntryPage(brandMark, "Signing in") {
                Text("Signing in to $origin…", color = MimosTheme.colors.muted)
            }
        SessionState.Unreachable ->
            EntryPage(brandMark, "No answer") {
                Text(
                    "Mimos could not reach $origin to renew your sign-in.",
                    color = MimosTheme.colors.muted,
                )
                Button("Try again", onClick = model::retry, modifier = fill)
                Button(
                    "Sign out",
                    onClick = onSignOut,
                    kind = ButtonKind.Secondary,
                    modifier = fill,
                )
            }
        is SessionState.SignedOut ->
            EntryPage(brandMark, "Sign in") {
                Text("Sign in to $origin", color = MimosTheme.colors.muted)
                current.notice?.let { Notice(it.message()) }
                Button("Sign in", onClick = model::signIn, modifier = fill)
                if (instance.config.signup == SignupPolicy.OPEN) {
                    Button(
                        "Create account",
                        onClick = model::createAccount,
                        kind = ButtonKind.Secondary,
                        modifier = fill,
                    )
                }
                Button(
                    "Change instance",
                    onClick = onChangeInstance,
                    kind = ButtonKind.Secondary,
                    modifier = fill,
                )
            }
    }
}
