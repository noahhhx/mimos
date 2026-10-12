import type { FuelTarget } from "@mimos/api-client";

import { dayLabel } from "./format.ts";
import { plannedTotalParts } from "./planned-totals.ts";

/** What a day's target is called wherever it shows: information about the day, never a score (ADR-0022). */
export const FUEL_LABEL = "Fuel for the day";

/** The accessible name of a day's fuel line, which tells one day's from another's. */
export function fuelLabelFor(date: string): string {
  return `Fuel for ${dayLabel(date)}`;
}

/**
 * A target's calories and macros, in the Planned line's order and format so
 * the two read side by side; none while the person's targets are off.
 */
export function fuelTargetParts(target: FuelTarget | undefined): string[] {
  return target ? plannedTotalParts(target) : [];
}
