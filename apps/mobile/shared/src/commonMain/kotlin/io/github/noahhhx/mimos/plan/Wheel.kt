package io.github.noahhhx.mimos.plan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.api.models.PanelWheelSegment
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.animationsEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The wheel's own units, as the web's SVG: a radius of 100, scaled to the space it has. */
private const val RADIUS = 100f
private const val ICON_MAX = 22.0

/** The web's `transition: transform 3s cubic-bezier(0.15, 0.6, 0.2, 1)`. */
private const val SPIN_MILLIS = 3000
private val SpinEasing = CubicBezierEasing(0.15f, 0.6f, 0.2f, 1f)

/**
 * A plugin's wheel (ADR-0017): segments in alternating fills, each icon near the rim, and a fixed
 * pointer at the top. When [spin] is set it turns forward to [landing], the one animation in the
 * app, then calls [onStop] and says where it stopped; with the system's animations off it lands at
 * once. Otherwise it rests on [landing], or where it is when there is none.
 */
@Composable
fun Wheel(
    segments: List<PanelWheelSegment>,
    landing: Int?,
    spin: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = segments.size
    val angle = remember {
        Animatable(if (spin || landing == null) 0f else restingAngle(landing, count).toFloat())
    }
    var stoppedOn by remember { mutableStateOf<String?>(null) }
    val animate = animationsEnabled()
    val stop by rememberUpdatedState(onStop)
    LaunchedEffect(segments, landing, spin) {
        if (landing == null) return@LaunchedEffect
        if (!spin) {
            val rest = restingAngle(landing, count)
            if (!samePosition(angle.value.toDouble(), rest)) angle.snapTo(rest.toFloat())
            return@LaunchedEffect
        }
        stoppedOn = null
        val target = spinTarget(angle.value.toDouble(), landing, count).toFloat()
        if (animate) angle.animateTo(target, tween(SPIN_MILLIS, easing = SpinEasing))
        else angle.snapTo(target)
        stoppedOn = segments.getOrNull(landing)?.label
        stop()
    }

    val colors = MimosTheme.colors
    val tones = listOf(colors.surface, colors.line, colors.hover)
    val measurer = rememberTextMeasurer()
    val description = "A wheel of $count" + (stoppedOn?.let { ". The wheel stopped on $it." } ?: "")
    Box(
        modifier.widthIn(max = 300.dp).fillMaxWidth().semantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        }
    ) {
        Box(
            Modifier.padding(top = 10.dp).fillMaxWidth().aspectRatio(1f).drawWithCache {
                val scale = size.minDimension / (2 * RADIUS)
                val center = Offset(size.width / 2, size.height / 2)
                val iconUnits = iconSize(count, (RADIUS - 20).toDouble(), ICON_MAX).toFloat()
                val rim = RADIUS - 6 - iconUnits / 2
                val iconStyle = TextStyle(fontSize = (iconUnits * scale).toDp().toSp())
                val icons = segments.map { segment ->
                    segment.icon
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            measurer.measure(it, iconStyle)
                        }
                }
                val sweep = segmentSweep(count).toFloat()
                val edge = Stroke(0.75f * scale)
                val ring = Stroke(1.5f * scale)
                val radius = RADIUS * scale
                onDrawBehind {
                    rotate(angle.value, center) {
                        for (index in 0 until count) {
                            val start = -90f + index * sweep
                            val tone = tones[segmentTone(index, count)]
                            drawArc(tone, start, sweep, useCenter = true)
                            drawArc(colors.bg, start, sweep, useCenter = true, style = edge)
                        }
                        icons.forEachIndexed { index, icon ->
                            if (icon == null) return@forEachIndexed
                            val middle = segmentCenter(index, count)
                            val radians = middle * PI / 180
                            val at =
                                Offset(
                                    center.x + (rim * scale * sin(radians)).toFloat(),
                                    center.y - (rim * scale * cos(radians)).toFloat(),
                                )
                            rotate(middle.toFloat(), at) {
                                val half = Offset(icon.size.width / 2f, icon.size.height / 2f)
                                drawText(icon, topLeft = at - half)
                            }
                        }
                        drawCircle(colors.muted, radius - 0.75f * scale, center, style = ring)
                        drawCircle(colors.bg, 9 * scale, center)
                        drawCircle(colors.muted, 9 * scale, center, style = ring)
                    }
                }
            }
        )
        Canvas(Modifier.align(Alignment.TopCenter).size(20.dp, 22.dp)) {
            val pointer =
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2, size.height)
                    close()
                }
            drawPath(pointer, colors.accent)
        }
    }
}
