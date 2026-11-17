package io.github.noahhhx.mimos.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.noahhhx.mimos.APP_NAME
import io.github.noahhhx.mimos.connect.Action
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.instance.SignupPolicy
import kotlinx.coroutines.CancellationException

/** What the signed-in shell shows of the person: loaded through the API with the bearer token. */
sealed interface WhoAmI {
    data object Loading : WhoAmI

    data class Known(val displayName: String) : WhoAmI

    data class Failed(val message: String) : WhoAmI
}

@Composable
fun SessionScreen(
    model: SessionModel,
    instance: Instance,
    brandMark: Painter,
    whoAmI: suspend () -> String,
    onChangeInstance: () -> Unit,
) {
    val state by model.state.collectAsState()
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(brandMark, contentDescription = null, modifier = Modifier.size(40.dp))
            BasicText(APP_NAME, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold))
        }
        val origin = instance.config.webUrl.origin
        when (val current = state) {
            SessionState.Refreshing -> BasicText("Opening Mimos…")
            SessionState.SigningIn -> BasicText("Signing in…")
            SessionState.Unreachable -> {
                BasicText("Mimos could not reach $origin to renew your sign-in.")
                Action("Try again", onClick = model::retry)
                Action("Sign out", onClick = model::signOut)
            }
            is SessionState.SignedOut -> {
                BasicText("Sign in to $origin")
                current.notice?.let { BasicText(it.message()) }
                Action("Sign in", onClick = model::signIn)
                if (instance.config.signup == SignupPolicy.OPEN) {
                    Action("Create account", onClick = model::createAccount)
                }
                Action("Change instance", onClick = onChangeInstance)
            }
            is SessionState.SignedIn -> {
                val who by
                    produceState<WhoAmI>(WhoAmI.Loading, instance) {
                        value =
                            try {
                                WhoAmI.Known(whoAmI())
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                WhoAmI.Failed(failure.message ?: "no answer")
                            }
                    }
                when (val known = who) {
                    WhoAmI.Loading -> BasicText("Loading…")
                    is WhoAmI.Known -> BasicText("Signed in as ${known.displayName}")
                    is WhoAmI.Failed -> BasicText("Signed in, but Mimos answered: ${known.message}")
                }
                BasicText(origin)
                Action("Sign out", onClick = model::signOut)
                Action("Change instance", onClick = onChangeInstance)
            }
        }
    }
}
