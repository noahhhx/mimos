import type { FuelTarget, Nutrition, PlannedDayTotal } from "@mimos/api-client";

import { FUEL_LABEL, fuelLabelFor } from "@/lib/fuel";
import { withoutNutritionNote } from "@/lib/planned-totals";

/**
 * A day's fuel in the plan's inspector: its target (while targets are on)
 * and what the person planned to eat, one row each under the same columns
 * so the two read against each other. Information, never a score (ADR-0022).
 */
export function DayFuel({
  date,
  target,
  planned,
}: {
  date: string;
  target: FuelTarget | undefined;
  planned: PlannedDayTotal | undefined;
}) {
  const note = planned ? withoutNutritionNote(planned) : null;
  return (
    <div className="inspector-section">
      <table className="day-fuel" aria-label={fuelLabelFor(date)}>
        <thead>
          <tr>
            <td />
            <th scope="col">kcal</th>
            <th scope="col">Protein</th>
            <th scope="col">Carbs</th>
            <th scope="col">Fat</th>
          </tr>
        </thead>
        <tbody>
          {target && <Row label={FUEL_LABEL} values={target} />}
          <Row label="Planned" values={planned && planned.meals > 0 ? planned.total : undefined} />
        </tbody>
      </table>
      {note && <p className="muted">{note}</p>}
    </div>
  );
}

function Row({ label, values }: { label: string; values: Nutrition | undefined }) {
  const round = (value: number | undefined, unit = "") => (value == null ? "–" : `${Math.round(value)}${unit}`);
  return (
    <tr>
      <th scope="row">{label}</th>
      <td>{round(values?.calories)}</td>
      <td>{round(values?.proteinG, " g")}</td>
      <td>{round(values?.carbsG, " g")}</td>
      <td>{round(values?.fatG, " g")}</td>
    </tr>
  );
}
