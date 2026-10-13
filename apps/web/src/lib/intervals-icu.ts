import type { FuelSex, IntervalsIcuConnection, IntervalsIcuHints } from "@mimos/api-client";

/** Where the Fuel page's intervals.icu section stands; `off` when this instance does not offer it (a 404). */
export type ConnectionLoad =
  | { state: "loading" }
  | { state: "off" }
  | { state: "error" }
  | { state: "ok"; data: IntervalsIcuConnection };

export const INTERVALS_ICU_FAILURES = {
  load: "Could not load your intervals.icu connection.",
  connect: "Could not connect intervals.icu. Try again.",
  sync: "Could not sync from intervals.icu. Try again.",
  disconnect: "Could not disconnect intervals.icu. Try again.",
};

/** "Connected as i1. Last synced 12 Oct, 21:14.": one line on where the connection stands. */
export function connectionSummary(connection: IntervalsIcuConnection, time: (iso: string) => string): string {
  switch (connection.status) {
    case "CONNECTED":
      return connection.lastSyncAt
        ? `Connected as ${connection.athleteId}. Last synced ${time(connection.lastSyncAt)}.`
        : `Connected as ${connection.athleteId}. Not synced yet.`;
    case "BROKEN":
      return connection.lastError ?? "intervals.icu no longer accepts the saved API key. Enter it again.";
    case "DISCONNECTED":
      return "Not connected.";
  }
}

/** Why the last sync did not work, or when the next may run, for a connection that still works. */
export function syncNote(connection: IntervalsIcuConnection, time: (iso: string) => string): string | null {
  if (connection.status !== "CONNECTED") {
    return null;
  }
  if (connection.retryAt) {
    return `intervals.icu asked Mimos to wait. Training syncs again after ${time(connection.retryAt)}.`;
  }
  return connection.lastError ?? null;
}

export type HintField = "weightKg" | "sex" | "ftpWatts";

/** A value from intervals.icu the person may copy into a field of their fuel profile. */
export type Suggestion = { field: HintField; value: string; text: string; action: string };

const SEX_NAMES: Record<FuelSex, string> = { FEMALE: "female", MALE: "male", UNSPECIFIED: "not given" };

/**
 * intervals.icu's weight, sex, and FTP where they differ from the form:
 * suggestions only, never saved until the person presses Save (ADR-0022).
 */
export function hintSuggestions(
  form: { weightKg: string; sex: FuelSex; ftpWatts: string },
  hints: IntervalsIcuHints | undefined,
): Suggestion[] {
  if (!hints) {
    return [];
  }
  const suggestions: Suggestion[] = [];
  if (hints.weightKg !== undefined && Number(form.weightKg) !== hints.weightKg) {
    suggestions.push({
      field: "weightKg",
      value: String(hints.weightKg),
      text: `intervals.icu has ${hints.weightKg} kg.`,
      action: `Use ${hints.weightKg} kg from intervals.icu`,
    });
  }
  if (hints.sex !== undefined && hints.sex !== form.sex) {
    suggestions.push({
      field: "sex",
      value: hints.sex,
      text: `intervals.icu has ${SEX_NAMES[hints.sex]}.`,
      action: `Use ${SEX_NAMES[hints.sex]} from intervals.icu`,
    });
  }
  if (hints.ftpWatts !== undefined && Number(form.ftpWatts) !== hints.ftpWatts) {
    suggestions.push({
      field: "ftpWatts",
      value: String(hints.ftpWatts),
      text: `intervals.icu has ${hints.ftpWatts} W.`,
      action: `Use ${hints.ftpWatts} W from intervals.icu`,
    });
  }
  return suggestions;
}

/** While the profile's body fat is empty, intervals.icu's already counts; this says so. */
export function bodyFatNote(formBodyFat: string, hints: IntervalsIcuHints | undefined): string | null {
  if (formBodyFat.trim() !== "" || hints?.bodyFatPercent === undefined) {
    return null;
  }
  return `Optional. While this is empty, your targets use ${hints.bodyFatPercent}% from intervals.icu.`;
}
