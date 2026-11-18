package io.github.noahhhx.mimos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.noahhhx.mimos.auth.AppAuthAuthorizer
import io.github.noahhhx.mimos.auth.BrowserLauncher
import io.github.noahhhx.mimos.auth.KeystoreTokenStore
import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.instance.discover
import io.github.noahhhx.mimos.session.SessionModel
import io.github.noahhhx.mimos.ui.ThemeModel
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Holds the app's models for as long as the app is open, so a connect or a sign-in in flight
 * survives the activity being recreated when the phone rotates.
 */
class AppViewModel(application: Application, debuggable: Boolean) : AndroidViewModel(application) {
    val browser = BrowserLauncher()
    val engine = OkHttp.create { if (debuggable) addInterceptor(RequestLog()) }
    private val theme = ThemeModel(SharedPreferencesThemeStore(application))

    val app =
        AppModel(
            scope = viewModelScope,
            connect =
                ConnectModel(SharedPreferencesInstanceStore(application)) {
                    discover(it, engine, allowLocalhostHttp = debuggable)
                },
            theme = theme,
            debug = debuggable,
            engine = engine,
            recipeSortStore = SharedPreferencesRecipeSortStore(application),
            createSession = { instance ->
                SessionModel(
                    authorizer =
                        AppAuthAuthorizer(
                            application,
                            instance.config.oidcAuthority,
                            browser,
                            allowLocalhostHttp = debuggable,
                        ),
                    store = KeystoreTokenStore(application),
                    authTheme = { theme.authTheme },
                    scope = viewModelScope,
                )
            },
        )

    override fun onCleared() {
        engine.close()
    }
}
