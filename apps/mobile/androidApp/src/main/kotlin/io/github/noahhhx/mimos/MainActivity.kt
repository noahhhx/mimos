package io.github.noahhhx.mimos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.ktor.client.engine.okhttp.OkHttp

private val engine by lazy { OkHttp.create() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(SharedPreferencesInstanceStore(applicationContext), engine) }
    }
}
