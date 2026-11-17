package io.github.noahhhx.mimos.auth

import android.content.Context
import androidx.core.net.toUri
import io.github.noahhhx.mimos.session.AuthorizeKind
import io.github.noahhhx.mimos.session.AuthorizeResult
import io.github.noahhhx.mimos.session.Authorizer
import io.github.noahhhx.mimos.session.OIDC_CLIENT_ID
import io.github.noahhhx.mimos.session.OIDC_REDIRECT_URI
import io.github.noahhhx.mimos.session.RefreshResult
import io.github.noahhhx.mimos.session.Tokens
import kotlin.coroutines.resume
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder

/**
 * AppAuth-Android signing in to one instance's Keycloak in the phone's browser (Custom Tabs when
 * the browser has them, any browser otherwise), then exchanging and refreshing tokens itself.
 */
class AppAuthAuthorizer(
    context: Context,
    private val authority: String,
    private val browser: BrowserLauncher,
    private val allowLocalhostHttp: Boolean,
) : Authorizer {
    private val context = context.applicationContext
    private val connectionBuilder: ConnectionBuilder =
        if (allowLocalhostHttp) LocalhostHttpConnectionBuilder
        else DefaultConnectionBuilder.INSTANCE
    private val redirectUri = OIDC_REDIRECT_URI.toUri()

    @Volatile private var discovered: AuthorizationServiceConfiguration? = null

    override suspend fun authorize(kind: AuthorizeKind, theme: String): AuthorizeResult =
        guarded({ AuthorizeResult.Failed(it.message ?: "Sign-in failed.") }) {
            startAuthorization(kind, theme)
        }

    override suspend fun refresh(refreshToken: String): RefreshResult =
        guarded({ RefreshResult.Unavailable }) { renew(refreshToken) }

    override suspend fun endSession(idToken: String?) {
        guarded({}) { closeSession(idToken) }
    }

    private suspend fun startAuthorization(kind: AuthorizeKind, theme: String): AuthorizeResult {
        val configuration =
            discover() ?: return AuthorizeResult.Failed("The sign-in server did not answer.")
        val request =
            AuthorizationRequest.Builder(
                    configuration,
                    OIDC_CLIENT_ID,
                    ResponseTypeValues.CODE,
                    redirectUri,
                )
                .setScope("openid")
                .setPrompt(if (kind == AuthorizeKind.CreateAccount) "create" else null)
                .setAdditionalParameters(mapOf("mimos_theme" to theme))
                .build()
        return withService { service ->
            val answer =
                browser.launch(service.getAuthorizationRequestIntent(request))
                    ?: return@withService AuthorizeResult.Cancelled
            val response = AuthorizationResponse.fromIntent(answer)
            val failure = AuthorizationException.fromIntent(answer)
            when {
                response != null -> exchange(service, response)
                failure?.isCancellation() == true -> AuthorizeResult.Cancelled
                else -> AuthorizeResult.Failed(failure?.errorDescription ?: "No answer came back.")
            }
        }
    }

    private suspend fun renew(refreshToken: String): RefreshResult {
        val configuration = discover() ?: return RefreshResult.Unavailable
        val request =
            TokenRequest.Builder(configuration, OIDC_CLIENT_ID)
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setRefreshToken(refreshToken)
                .build()
        return withService { service ->
            when (val outcome = tokenCall(service, request)) {
                is TokenOutcome.Answer ->
                    outcome.response
                        .toTokens(fallbackRefreshToken = refreshToken)
                        ?.let(RefreshResult::Granted) ?: RefreshResult.Unavailable
                is TokenOutcome.Error ->
                    if (outcome.exception.isInvalidGrant()) RefreshResult.InvalidGrant
                    else RefreshResult.Unavailable
            }
        }
    }

    private suspend fun closeSession(idToken: String?) {
        if (idToken == null) return
        val configuration = discover() ?: return
        if (configuration.endSessionEndpoint == null) return
        val request =
            EndSessionRequest.Builder(configuration)
                .setIdTokenHint(idToken)
                .setPostLogoutRedirectUri(redirectUri)
                .build()
        withService { service -> browser.launch(service.getEndSessionRequestIntent(request)) }
    }

    private suspend fun <T> guarded(onFailure: (Exception) -> T, block: suspend () -> T): T =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onFailure(failure)
        }

    private suspend fun exchange(
        service: AuthorizationService,
        response: AuthorizationResponse,
    ): AuthorizeResult =
        when (val outcome = tokenCall(service, response.createTokenExchangeRequest())) {
            is TokenOutcome.Answer ->
                outcome.response.toTokens()?.let(AuthorizeResult::Granted)
                    ?: AuthorizeResult.Failed("The sign-in server sent no refresh token.")
            is TokenOutcome.Error ->
                AuthorizeResult.Failed(
                    outcome.exception.errorDescription ?: "The sign-in server refused the sign-in."
                )
        }

    private suspend fun <T> withService(use: suspend (AuthorizationService) -> T): T {
        val service =
            AuthorizationService(
                context,
                AppAuthConfiguration.Builder()
                    .setConnectionBuilder(connectionBuilder)
                    .setSkipIssuerHttpsCheck(allowLocalhostHttp)
                    .build(),
            )
        try {
            return use(service)
        } finally {
            service.dispose()
        }
    }

    private suspend fun discover(): AuthorizationServiceConfiguration? {
        discovered?.let {
            return it
        }
        return suspendCancellableCoroutine { continuation ->
            AuthorizationServiceConfiguration.fetchFromIssuer(
                authority.toUri(),
                { configuration, _ ->
                    discovered = configuration
                    continuation.resume(configuration)
                },
                connectionBuilder,
            )
        }
    }

    private suspend fun tokenCall(service: AuthorizationService, request: TokenRequest) =
        suspendCancellableCoroutine<TokenOutcome> { continuation ->
            service.performTokenRequest(request) { response, exception ->
                continuation.resume(
                    when {
                        response != null -> TokenOutcome.Answer(response)
                        else ->
                            TokenOutcome.Error(
                                exception ?: AuthorizationException.GeneralErrors.NETWORK_ERROR
                            )
                    }
                )
            }
        }

    private sealed interface TokenOutcome {
        data class Answer(val response: TokenResponse) : TokenOutcome

        data class Error(val exception: AuthorizationException) : TokenOutcome
    }
}

private fun TokenResponse.toTokens(fallbackRefreshToken: String? = null): Tokens? {
    val access = accessToken ?: return null
    val refresh = refreshToken ?: fallbackRefreshToken ?: return null
    return Tokens(
        accessToken = access,
        accessExpiresAt = Instant.fromEpochMilliseconds(accessTokenExpirationTime ?: 0L),
        refreshToken = refresh,
        idToken = idToken,
    )
}

private fun AuthorizationException.isCancellation() =
    type == AuthorizationException.TYPE_GENERAL_ERROR &&
        code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code

private fun AuthorizationException.isInvalidGrant() =
    type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR &&
        code == AuthorizationException.TokenRequestErrors.INVALID_GRANT.code
