package io.github.noahhhx.mimos

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.res.painterResource
import io.ktor.client.engine.okhttp.OkHttp

private val engine by lazy { OkHttp.create() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        setContent {
            App(
                SharedPreferencesInstanceStore(applicationContext),
                engine,
                debuggable,
                brandMark = painterResource(R.drawable.brand_duck),
            )
        }
    }
}
