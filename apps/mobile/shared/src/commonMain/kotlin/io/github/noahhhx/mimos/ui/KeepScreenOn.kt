package io.github.noahhhx.mimos.ui

import androidx.compose.runtime.Composable

/** Keeps the screen from sleeping while this is in the composition: hands busy cooking. */
@Composable expect fun KeepScreenOn()
