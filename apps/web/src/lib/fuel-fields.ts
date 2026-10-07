export type FuelField = "weightKg" | "bodyFatPercent" | "ftpWatts" | "proteinGPerKg";

export const FIELD_ERRORS: Record<FuelField, string> = {
  weightKg: "Enter a weight from 30 to 250 kg.",
  bodyFatPercent: "Enter body fat from 3 to 60%, or leave it empty.",
  ftpWatts: "Enter a whole number of watts from 50 to 600, or leave it empty.",
  proteinGPerKg: "Enter protein from 1.2 to 3.0 g per kg.",
};

export function namedFields(detail: string | undefined): FuelField[] {
  return (detail ?? "")
    .split("; ")
    .map((part) => part.split(" ")[0])
    .filter((name): name is FuelField => Object.hasOwn(FIELD_ERRORS, name));
}
