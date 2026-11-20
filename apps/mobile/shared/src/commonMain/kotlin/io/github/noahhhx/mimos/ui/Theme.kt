package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode

private val LocalColors = staticCompositionLocalOf { MimosColors.Light }
private val LocalTypography =
    staticCompositionLocalOf<MimosTypography> {
        error("MimosTheme is not in the composition")
    }
private val LocalSpacing = staticCompositionLocalOf { MimosSpacing() }

/** Where components read the design from: `MimosTheme.colors`, `.typography`, `.spacing`. */
object MimosTheme {
    val colors: MimosColors
        @Composable @ReadOnlyComposable get() = LocalColors.current

    val typography: MimosTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current

    val spacing: MimosSpacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
}

/** Evening Kitchen for [choice]. Presses show no ripple: the design has no motion. */
@Composable
fun MimosTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalColors provides choice.colors,
        LocalTypography provides rememberMimosTypography(),
        LocalSpacing provides MimosSpacing(),
        LocalIndication provides NoIndication,
        content = content,
    )
}

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() = drawContent()
        }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = -1
}
