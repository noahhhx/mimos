package io.github.noahhhx.mimos.ui

import androidx.compose.runtime.Composable

/**
 * Whether the system lets apps animate: false once a person turns animations off (on Android,
 * "Remove animations", an animator duration scale of 0). The plugin wheel is the app's one
 * animation, and it lands at once when this is false.
 */
@Composable expect fun animationsEnabled(): Boolean
