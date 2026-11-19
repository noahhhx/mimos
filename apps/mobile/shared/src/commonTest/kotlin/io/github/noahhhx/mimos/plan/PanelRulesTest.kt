package io.github.noahhhx.mimos.plan

import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.client.PanelBlock
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

@Serializable private class LandingCase(val blocks: List<PanelBlock>, val expected: Boolean)

@Serializable
private class ShownCase(
    val name: String,
    val blocks: List<PanelBlock>,
    val spinning: Boolean,
    val expected: List<String>,
)

@Serializable
private class PanelCases(
    val hasLandingCases: List<LandingCase>,
    val shownBlocksCases: List<ShownCase>,
)

@Serializable private class SweepCase(val count: Int, val expected: Double)

@Serializable private class CenterCase(val index: Int, val count: Int, val expected: Double)

@Serializable private class RestCase(val landing: Int, val count: Int, val expected: Double)

@Serializable
private class SpinCase(
    val current: Double,
    val landing: Int,
    val count: Int,
    val turns: Int = SPIN_TURNS,
    val expected: Double,
)

@Serializable private class PositionCase(val angle: Double, val other: Double, val expected: Boolean)

@Serializable private class ToneCase(val count: Int, val expected: List<Int>)

@Serializable
private class IconCase(val count: Int, val rimRadius: Double, val max: Double, val expected: Double)

@Serializable
private class WheelCases(
    val spinTurns: Int,
    val segmentSweepCases: List<SweepCase>,
    val segmentCenterCases: List<CenterCase>,
    val restingAngleCases: List<RestCase>,
    val spinTargetCases: List<SpinCase>,
    val samePositionCases: List<PositionCase>,
    val segmentToneCases: List<ToneCase>,
    val iconSizeCases: List<IconCase>,
)

private val panels = MimosJson.decodeFromString<PanelCases>(TestCases.read("panels.json"))
private val wheel = MimosJson.decodeFromString<WheelCases>(TestCases.read("wheel.json"))

private fun assertNear(expected: Double, actual: Double, what: Any) =
    assertTrue(abs(expected - actual) < 1e-9, "$what: $actual, expected $expected")

/** The cases are the web's too (`libraries/test-cases`). */
class PanelRulesTest {
    @Test
    fun `only a wheel with a landing has one`() {
        for (each in panels.hasLandingCases) {
            assertEquals(each.expected, hasLanding(each.blocks), each.blocks.toString())
        }
    }

    @Test
    fun `the blocks shown and their keys are the web's`() {
        for (each in panels.shownBlocksCases) {
            val shown = shownBlocks(each.blocks, each.spinning)
            assertEquals(each.expected, shown.map { it.key }, each.name)
            assertEquals(each.blocks.take(each.expected.size), shown.map { it.block }, each.name)
        }
    }

    @Test
    fun `a block of an unknown type is kept as unknown, not dropped`() {
        val unknown = panels.shownBlocksCases.flatMap { it.blocks }.single { it.type == "confetti" }
        assertTrue(unknown is PanelBlock.Unknown)
    }

    @Test
    fun `the wheel's geometry is the web's`() {
        assertEquals(wheel.spinTurns, SPIN_TURNS)
        for (each in wheel.segmentSweepCases) {
            assertNear(each.expected, segmentSweep(each.count), each.count)
        }
        for (each in wheel.segmentCenterCases) {
            assertNear(each.expected, segmentCenter(each.index, each.count), each.index)
        }
        for (each in wheel.restingAngleCases) {
            assertNear(each.expected, restingAngle(each.landing, each.count), each.landing)
        }
        for (each in wheel.spinTargetCases) {
            val target = spinTarget(each.current, each.landing, each.count, each.turns)
            assertNear(each.expected, target, each.current)
        }
        for (each in wheel.samePositionCases) {
            assertEquals(each.expected, samePosition(each.angle, each.other), "${each.angle}")
        }
        for (each in wheel.segmentToneCases) {
            assertEquals(each.expected, each.expected.indices.map { segmentTone(it, each.count) })
        }
        for (each in wheel.iconSizeCases) {
            assertEquals(each.expected, iconSize(each.count, each.rimRadius, each.max))
        }
    }

    @Test
    fun `a spin always turns forward and lands on its segment`() {
        for (current in listOf(0.0, 90.0, 315.0, 1800.0, 3779.25)) {
            for (landing in 0 until 9) {
                val target = spinTarget(current, landing, 9)
                val travel = target - current
                assertTrue(travel >= SPIN_TURNS * 360 && travel < (SPIN_TURNS + 1) * 360, "$travel")
                assertTrue(samePosition(target, restingAngle(landing, 9)), "$current, $landing")
            }
        }
    }
}
