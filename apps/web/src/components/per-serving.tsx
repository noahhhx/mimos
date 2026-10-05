import type { Nutrition } from "@mimos/api-client";

/** Per-serving calories and macros as large figures; an unknown value shows as a dash. */
export function PerServing({ nutrition }: { nutrition: Nutrition }) {
  const figure = (value: number | undefined, unit: string) => (value != null ? `${Math.round(value)} ${unit}` : "–");
  return (
    <dl className="per-serving">
      <div>
        <dt>Calories</dt>
        <dd>{figure(nutrition.calories, "kcal")}</dd>
      </div>
      <div>
        <dt>Protein</dt>
        <dd>{figure(nutrition.proteinG, "g")}</dd>
      </div>
      <div>
        <dt>Carbs</dt>
        <dd>{figure(nutrition.carbsG, "g")}</dd>
      </div>
      <div>
        <dt>Fat</dt>
        <dd>{figure(nutrition.fatG, "g")}</dd>
      </div>
    </dl>
  );
}
