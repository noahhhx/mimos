import type { FuelTarget, PlannedDayTotal, TrainingSession } from "@mimos/api-client";

import { dayLabel } from "./format.ts";
import { SPORTS } from "./fuel.ts";

/**
 * The plan page's ledger: a table of days by meals whose cells show names
 * only, and an inspector that holds the controls for whatever is selected,
 * a planned meal or a day.
 */
export type LedgerSelection = { kind: "entry"; id: string } | { kind: "day"; date: string } | null;

/** Choosing what is already selected closes the inspector; anything else selects it. */
export function toggleSelection(current: LedgerSelection, next: Exclude<LedgerSelection, null>): LedgerSelection {
  return current !== null && selectionKey(current) === selectionKey(next) ? null : next;
}

function selectionKey(selection: Exclude<LedgerSelection, null>): string {
  return selection.kind === "entry" ? `entry:${selection.id}` : `day:${selection.date}`;
}

/**
 * The selection that still stands after the week reloads or the view
 * changes: a meal that is gone or hidden, or a day outside the week, is no
 * longer selected.
 */
export function standingSelection(
  selection: LedgerSelection,
  shownEntryIds: readonly string[],
  days: readonly string[],
): LedgerSelection {
  if (selection === null) {
    return null;
  }
  if (selection.kind === "entry") {
    return shownEntryIds.includes(selection.id) ? selection : null;
  }
  return days.includes(selection.date) ? selection : null;
}

/** A ledger column's head: the short weekday and the day of the month, in the user's locale. */
export function dayHead(isoDate: string): { weekday: string; date: number } {
  const day = new Date(`${isoDate}T00:00:00Z`);
  return { weekday: day.toLocaleDateString(undefined, { weekday: "short", timeZone: "UTC" }), date: day.getUTCDate() };
}

/** "Ride 90 min": a session in a ledger cell, without its intensity, kind, or source. */
export function sessionShort(session: Pick<TrainingSession, "sport" | "durationMinutes">): string {
  return `${SPORTS[session.sport]} ${session.durationMinutes} min`;
}

/**
 * A day's planned calories against its fuel target, as a ledger cell shows
 * them: the planned figure big, then "of 3120 kcal" while targets are on, or
 * "kcal" while they are off. A day with no meals has planned nothing yet; a
 * day whose meals' calories are unknown shows a dash, never a zero. `said`
 * is the figure in words, for the cell's accessible name. Information about
 * the day, never a score (ADR-0022).
 */
export function ledgerFuel(
  target: Pick<FuelTarget, "calories"> | undefined,
  planned: Pick<PlannedDayTotal, "meals" | "total"> | undefined,
): { value: string; detail: string | null; said: string } {
  const meals = planned?.meals ?? 0;
  const calories = meals > 0 ? planned?.total.calories : undefined;
  const value = calories == null ? "–" : String(Math.round(calories));
  const said = calories != null ? value : meals > 0 ? "calories unknown" : "nothing yet";
  if (target?.calories != null) {
    return { value, detail: `of ${Math.round(target.calories)} kcal`, said };
  }
  return { value, detail: calories == null ? null : "kcal", said };
}

/** The accessible name of a day's planned cell, which tells one day's from another's. */
export function ledgerFuelLabel(date: string, fuel: { detail: string | null; said: string }): string {
  return `Planned for ${dayLabel(date)}: ${fuel.said}${fuel.detail ? ` ${fuel.detail}` : ""}`;
}
