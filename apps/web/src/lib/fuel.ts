import type { FuelTarget, TrainingIntensity, TrainingKind, TrainingSession, TrainingSport } from "@mimos/api-client";

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

export const TRAINING_LABEL = "Training";

export function trainingLabelFor(date: string): string {
  return `Training for ${dayLabel(date)}`;
}

export function addTrainingLabelFor(date: string): string {
  return `Add training for ${dayLabel(date)}`;
}

export const TRAINING_FAILURES = {
  add: "Could not add that training.",
  remove: "Could not remove that training.",
};

export const SPORTS: Record<TrainingSport, string> = {
  RIDE: "Ride",
  RUN: "Run",
  SWIM: "Swim",
  STRENGTH: "Strength",
  OTHER: "Other",
};

export const INTENSITIES: Record<TrainingIntensity, string> = {
  EASY: "Easy",
  ENDURANCE: "Endurance",
  HARD: "Hard",
};

const KINDS: Record<TrainingKind, string> = {
  PLANNED: "Planned",
  COMPLETED: "Completed",
};

/** "Ride", "90 min", "Endurance", "Planned": a session's sport, length, intensity when known, and kind. */
export function sessionParts(session: TrainingSession): string[] {
  return [
    SPORTS[session.sport],
    `${session.durationMinutes} min`,
    ...(session.intensity ? [INTENSITIES[session.intensity]] : []),
    KINDS[session.kind],
  ];
}

/** "Remove Ride, 90 min": the name of a session's remove button, which tells one session from another's. */
export function removeSessionLabel(session: TrainingSession): string {
  return `Remove ${SPORTS[session.sport]}, ${session.durationMinutes} min`;
}
