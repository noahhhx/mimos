import type { ImportReport } from "@mimos/api-client";

/** The download's file name: the export's date, so backups sort by when they were taken. */
export function exportFileName(exportedAt: string): string {
  return `mimos-export-${exportedAt.slice(0, 10)}.json`;
}

/**
 * An export file's contents, parsed for the import call. The API judges
 * whether it is a Mimos export (and which version); this only rules out
 * files that are not a JSON object at all.
 */
export function parseExportFile(
  text: string,
): { ok: true; document: Record<string, unknown> } | { ok: false; error: string } {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { ok: false, error: "That file is not a Mimos export: it is not valid JSON." };
  }
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    return { ok: false, error: "That file is not a Mimos export." };
  }
  return { ok: true, document: parsed as Record<string, unknown> };
}

/** "Imported 2 recipes, 1 planned meal, no shopping lists and 3 logged meals." */
export function importSummary(report: ImportReport): string {
  const parts = [
    count(report.recipes, "recipe"),
    count(report.plannedMeals, "planned meal"),
    count(report.shoppingLists, "shopping list"),
    count(report.mealLogs, "logged meal"),
  ];
  return `Imported ${parts.slice(0, -1).join(", ")} and ${parts.at(-1)}.`;
}

function count(n: number, noun: string): string {
  return n === 0 ? `no ${noun}s` : `${n} ${noun}${n === 1 ? "" : "s"}`;
}
