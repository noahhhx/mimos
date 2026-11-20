package io.github.noahhhx.mimos.ui

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable

@Composable actual fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()
