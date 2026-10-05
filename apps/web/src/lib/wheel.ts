/**
 * Geometry of a plugin's wheel (ADR-0017). Angles are degrees, clockwise
 * from the top, where the pointer sits. Segment 0 starts at the top and
 * the rest follow clockwise; rotating the wheel by `r` moves every segment
 * clockwise by `r`.
 */

/** Full turns a spin makes before it settles, so a short spin still reads as a spin. */
export const SPIN_TURNS = 5;

function mod360(angle: number): number {
  return ((angle % 360) + 360) % 360;
}

/** The angle of one segment. */
export function segmentSweep(count: number): number {
  return 360 / count;
}

/** The middle of segment `index`, on the unrotated wheel. */
export function segmentCenter(index: number, count: number): number {
  return (index + 0.5) * segmentSweep(count);
}

/** The rotation in [0, 360) that puts the middle of segment `landing` under the pointer. */
export function restingAngle(landing: number, count: number): number {
  return mod360(-segmentCenter(landing, count));
}

/**
 * Where a spin from `current` ends: at least `turns` full turns further
 * clockwise, and less than one turn past that, with `landing` under the
 * pointer. A wheel only ever turns forward, however many spins came before.
 */
export function spinTarget(current: number, landing: number, count: number, turns = SPIN_TURNS): number {
  return current + turns * 360 + mod360(restingAngle(landing, count) - current);
}

/** Whether `angle` shows the wheel at the same position as `other`. */
export function samePosition(angle: number, other: number): boolean {
  const apart = mod360(angle - other);
  return apart < 1e-6 || 360 - apart < 1e-6;
}

/**
 * Which of three fills segment `index` takes. They alternate; with an odd
 * count the last segment would meet the first in the same fill, so it
 * takes the third.
 */
export function segmentTone(index: number, count: number): 0 | 1 | 2 {
  if (count % 2 === 1 && index === count - 1) {
    return 2;
  }
  return index % 2 === 0 ? 0 : 1;
}

/**
 * The point at `angle` and `radius` from the wheel's center, in SVG
 * coordinates (y grows downward). Rounded so the server and the browser,
 * whose trigonometry can differ in the last digit, render the same markup.
 */
export function polar(angle: number, radius: number): { x: number; y: number } {
  const radians = (angle * Math.PI) / 180;
  const round = (n: number) => Math.round(n * 1000) / 1000 || 0;
  return { x: round(radius * Math.sin(radians)), y: round(-radius * Math.cos(radians)) };
}

/** An SVG path for segment `index`: a slice from the center out to `radius`. */
export function segmentPath(index: number, count: number, radius: number): string {
  const start = polar(index * segmentSweep(count), radius);
  const end = polar((index + 1) * segmentSweep(count), radius);
  const largeArc = segmentSweep(count) > 180 ? 1 : 0;
  return `M0 0L${start.x} ${start.y}A${radius} ${radius} 0 ${largeArc} 1 ${end.x} ${end.y}Z`;
}

/** A segment's icon size, so icons near the rim never crowd their neighbors: at most `max`, shrinking as segments narrow. */
export function iconSize(count: number, rimRadius: number, max: number): number {
  const arc = (2 * Math.PI * rimRadius) / count;
  return Math.min(max, Number((arc * 0.7).toFixed(2)));
}
