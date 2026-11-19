package io.github.noahhhx.mimos.plan

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.round

/*
 * Geometry of a plugin's wheel (ADR-0017), ported from the web's `apps/web/src/lib/wheel.ts`.
 * Angles are degrees, clockwise from the top, where the pointer sits. Segment 0 starts at the top
 * and the rest follow clockwise; rotating the wheel by `r` moves every segment clockwise by `r`.
 * `libraries/test-cases/wheel.json` holds the cases both sides run.
 */

/** Full turns a spin makes before it settles, so a short spin still reads as a spin. */
const val SPIN_TURNS = 5

private fun mod360(angle: Double): Double = ((angle % 360) + 360) % 360

/** The angle of one segment. */
fun segmentSweep(count: Int): Double = 360.0 / count

/** The middle of segment [index], on the unrotated wheel. */
fun segmentCenter(index: Int, count: Int): Double = (index + 0.5) * segmentSweep(count)

/** The rotation in [0, 360) that puts the middle of segment [landing] under the pointer. */
fun restingAngle(landing: Int, count: Int): Double = mod360(-segmentCenter(landing, count))

/**
 * Where a spin from [current] ends: at least [turns] full turns further clockwise, and less than
 * one turn past that, with [landing] under the pointer. A wheel only ever turns forward.
 */
fun spinTarget(current: Double, landing: Int, count: Int, turns: Int = SPIN_TURNS): Double =
    current + turns * 360 + mod360(restingAngle(landing, count) - current)

/** Whether [angle] shows the wheel at the same position as [other]. */
fun samePosition(angle: Double, other: Double): Boolean {
    val apart = mod360(angle - other)
    return apart < 1e-6 || 360 - apart < 1e-6
}

/**
 * Which of three fills segment [index] takes. They alternate; with an odd count the last segment
 * would meet the first in the same fill, so it takes the third.
 */
fun segmentTone(index: Int, count: Int): Int =
    when {
        count % 2 == 1 && index == count - 1 -> 2
        index % 2 == 0 -> 0
        else -> 1
    }

/**
 * A segment's icon size, so icons near the rim never crowd their neighbours: at most [max],
 * shrinking as segments narrow.
 */
fun iconSize(count: Int, rimRadius: Double, max: Double): Double {
    val arc = 2 * PI * rimRadius / count
    return min(max, round(arc * 0.7 * 100) / 100)
}
