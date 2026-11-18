package io.github.noahhhx.mimos

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.noahhhx.mimos.ui.ThemeChoice
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val models: AppViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                return AppViewModel(application as Application, debuggable) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val browser =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                models.browser.deliver(it.data)
            }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                models.browser.request.collect { request ->
                    if (request != null && request.claim()) browser.launch(request.intent)
                }
            }
        }
        // The theme is the person's choice in the app, never the system's (ADR-0021), so the bars
        // and the window behind the first frame follow it too.
        lifecycleScope.launch {
            models.app.theme.state.collect { choice ->
                window.setBackgroundDrawable(choice.colors.bg.toArgb().toDrawable())
                enableEdgeToEdge(
                    statusBarStyle = choice.systemBars(),
                    navigationBarStyle = choice.systemBars(),
                )
            }
        }
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_GALLERY, false)) {
            models.app.openGallery()
        }
        setContent {
            App(models.app, models.engine, brandMark = painterResource(R.drawable.brand_duck))
        }
    }

    companion object {
        /**
         * `adb shell am start ... --ez gallery true` opens the component gallery in a debug build.
         */
        const val EXTRA_GALLERY = "gallery"
    }
}

private fun ThemeChoice.systemBars(): SystemBarStyle =
    when (this) {
        ThemeChoice.Light -> SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        ThemeChoice.Dark -> SystemBarStyle.dark(Color.TRANSPARENT)
    }
