import type { PanelBlock } from "@mimos/api-client";

/**
 * A week panel's blocks (ADR-0017) as the plan page shows them, kept free
 * of the browser so they can be tested on node --test.
 */

/** Whether the blocks hold a wheel that has somewhere to land. */
export function hasLanding(blocks: PanelBlock[]): boolean {
  return blocks.some((block) => block.type === "wheel" && block.landing !== undefined);
}

/**
 * The blocks to show, each with a React key. While a wheel spins, the
 * blocks after it stay hidden so the result is not given away. A block's
 * key is its type and how many of that type came before it, so the wheel
 * keeps its key, and its current angle, when the plugin re-renders blocks
 * around it.
 */
export function shownBlocks(blocks: PanelBlock[], spinning: boolean): { key: string; block: PanelBlock }[] {
  const seen = new Map<string, number>();
  const shown: { key: string; block: PanelBlock }[] = [];
  for (const block of blocks) {
    const ordinal = seen.get(block.type) ?? 0;
    seen.set(block.type, ordinal + 1);
    shown.push({ key: `${block.type}:${ordinal}`, block });
    if (spinning && block.type === "wheel" && block.landing !== undefined) {
      break;
    }
  }
  return shown;
}

/** The detail of an RFC 9457 problem the API answered with, or `fallback`. */
export function problemDetail(error: unknown, fallback: string): string {
  if (typeof error === "object" && error !== null && "detail" in error && typeof error.detail === "string") {
    return error.detail;
  }
  return fallback;
}
