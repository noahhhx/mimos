package io.github.noahhhx.mimos.plan

import io.github.noahhhx.mimos.client.PanelBlock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * A week panel's blocks (ADR-0017) as the plan shows them, ported from the web's
 * `apps/web/src/lib/panels.ts`. `libraries/test-cases/panels.json` holds the cases both sides run.
 */

/** The block's `type` as the server sent it, a type this app does not know included. */
val PanelBlock.type: String
    get() =
        when (this) {
            is PanelBlock.Text -> block.type
            is PanelBlock.Highlight -> block.type
            is PanelBlock.Wheel -> block.type
            is PanelBlock.Actions -> block.type
            is PanelBlock.Unknown -> (json["type"] as? JsonPrimitive)?.contentOrNull ?: ""
        }

/** Whether the blocks hold a wheel that has somewhere to land. */
fun hasLanding(blocks: List<PanelBlock>): Boolean = blocks.any {
    it is PanelBlock.Wheel && it.block.landing != null
}

/** A block to show, with the key that keeps its identity, and a wheel its angle, across answers. */
data class ShownBlock(val key: String, val block: PanelBlock)

/**
 * The blocks to show. While a wheel spins, the blocks after it stay hidden so the result is not
 * given away. A block's key is its type and how many of that type came before it, so the wheel
 * keeps its key when the plugin re-renders blocks around it.
 */
fun shownBlocks(blocks: List<PanelBlock>, spinning: Boolean): List<ShownBlock> {
    val seen = mutableMapOf<String, Int>()
    val shown = mutableListOf<ShownBlock>()
    for (block in blocks) {
        val ordinal = seen[block.type] ?: 0
        seen[block.type] = ordinal + 1
        shown += ShownBlock("${block.type}:$ordinal", block)
        if (spinning && block is PanelBlock.Wheel && block.block.landing != null) break
    }
    return shown
}
