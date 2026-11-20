package io.github.noahhhx.mimos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.noahhhx.mimos.account.AndroidDocumentStore
import io.github.noahhhx.mimos.auth.AppAuthAuthorizer
import io.github.noahhhx.mimos.auth.BrowserLauncher
import io.github.noahhhx.mimos.auth.KeystoreTokenStore
import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.instance.discover
import io.github.noahhhx.mimos.recipes.pictures.AndroidPictureShrinker
import io.github.noahhhx.mimos.session.SessionModel
import io.github.noahhhx.mimos.ui.ThemeModel
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Holds the app's models for as long as the app is open, so a connect or a sign-in in flight
 * survives the activity being recreated when the phone rotates.
 */
class AppViewModel(application: Application, devBuild: Boolean) : AndroidViewModel(application) {
    val browser = BrowserLauncher()
    val engine = OkHttp.create { if (devBuild) addInterceptor(RequestLog()) }
    private val theme = ThemeModel(SharedPreferencesThemeStore(application))
    private val network = AndroidNetworkWatcher(application)

    val app =
        AppModel(
            scope = viewModelScope,
            connect =
                ConnectModel(SharedPreferencesInstanceStore(application)) {
                    discover(it, engine, allowLocalhostHttp = devBuild)
                },
            theme = theme,
            debug = devBuild,
            engine = engine,
            recipeSortStore = SharedPreferencesRecipeSortStore(application),
            planViewStore = SharedPreferencesPlanViewStore(application),
            pictureShrinker = AndroidPictureShrinker(application),
            documents = AndroidDocumentStore(application),
            shoppingStore = SharedPreferencesShoppingStore(application),
            network = network,
            createSession = { instance ->
                SessionModel(
                    authorizer =
                        AppAuthAuthorizer(
                            application,
                            instance.config.oidcAuthority,
                            browser,
                            allowLocalhostHttp = devBuild,
                        ),
                    store = KeystoreTokenStore(application),
                    authTheme = { theme.authTheme },
                    scope = viewModelScope,
                )
            },
        )

    override fun onCleared() {
        network.close()
        engine.close()
    }
}
