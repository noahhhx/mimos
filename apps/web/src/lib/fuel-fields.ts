export type FuelField = "weightKg" | "bodyFatPercent" | "ftpWatts" | "proteinGPerKg";

export const FIELD_ERRORS: Record<FuelField, string> = {
  weightKg: "Enter a weight from 30 to 250 kg.",
  bodyFatPercent: "Enter body fat from 3 to 60%, or leave it empty.",
  ftpWatts: "Enter a whole number of watts from 50 to 600, or leave it empty.",
  proteinGPerKg: "Enter protein from 1.2 to 3.0 g per kg.",
};

export type TrainingField = "durationMinutes";

export const TRAINING_FIELD_ERRORS: Record<TrainingField, string> = {
  durationMinutes: "Enter a whole number of minutes from 1 to 1440.",
};

/** The fields of `errors` that a 400's detail ("field message; field message") names. */
export function namedFields<F extends string>(detail: string | undefined, errors: Record<F, string>): F[] {
  return (detail ?? "")
    .split("; ")
    .map((part) => part.split(" ")[0])
    .filter((name): name is F => Object.hasOwn(errors, name));
}
