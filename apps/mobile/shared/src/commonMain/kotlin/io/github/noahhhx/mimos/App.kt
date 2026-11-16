package io.github.noahhhx.mimos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

const val APP_NAME = "Mimos"

@Composable
fun App() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText(APP_NAME) }
}
