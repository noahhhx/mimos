"use client";

import { useCallback, useEffect, useState } from "react";

import {
  createMealLog,
  deleteMealLog,
  getMyFuel,
  listIngredients,
  listLibraryRecipes,
  listMealLogs,
  listMyRecipes,
  summarizeMealLogs,
  type CatalogIngredient,
  type DailyLogSummary,
  type FuelDay,
  type MealLog,
  type RecipeSummary,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { Combobox } from "@/components/combobox";
import { PageHeader } from "@/components/page-header";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { apiClient } from "@/lib/api";
import {
  MEAL_TYPES,
  addDays,
  dayLabel,
  formatKcal,
  formatServings,
  mealLabel,
  mondayOf,
  nutritionCells,
  todayIso,
  weekDays,
  type MealTypeValue,
} from "@/lib/format";
import { FUEL_LABEL } from "@/lib/fuel";
import { describeRecipe } from "@/lib/ingredient-links";
import {
  EMPTY_NUTRITION,
  EMPTY_WHAT,
  defaultAmount,
  describeIngredient,
  describeTotal,
  logBody,
  measuredOf,
  measuredTotal,
  parseAmount,
  parseServings,
  recipeTotal,
  searchWhat,
  typeWhat,
  unitLabel,
  unitsFor,
  whatText,
  type AmountFields,
  type LogWhat,
  type NutritionFields,
  type UnitSymbol,
  type WhatOption,
} from "@/lib/log-entry";

/**
 * The food diary: per-day calorie and macro totals for the week, the
 * selected day's meals, and a log for the unplanned ones: a recipe, in
 * servings, an ingredient, by amount, or anything else counted by hand.
 * Information, not judgement — no streaks, no scolding.
 */
export default function LogPage() {
  const { user } = useAuth();
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [selectedDay, setSelectedDay] = useState(todayIso());
  const [logs, setLogs] = useState<MealLog[] | null>(null);
  const [summary, setSummary] = useState<DailyLogSummary[] | null>(null);
  // Each day's target shows only while the person's targets are on (ADR-0022).
  const [fuel, setFuel] = useState<FuelDay[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [recipes, setRecipes] = useState<RecipeSummary[]>([]);
  const [ingredients, setIngredients] = useState<CatalogIngredient[]>([]);
  const [what, setWhat] = useState<LogWhat>(EMPTY_WHAT);
  const [mealType, setMealType] = useState<MealTypeValue>("SNACK");
  const [servings, setServings] = useState("1");
  const [nutrition, setNutrition] = useState<NutritionFields>(EMPTY_NUTRITION);
  const [amount, setAmount] = useState<AmountFields>(defaultAmount("PER_100_G"));

  const reload = useCallback(async () => {
    const to = addDays(weekStart, 6);
    const [logsResult, summaryResult, fuelResult] = await Promise.all([
      listMealLogs({ client: apiClient, query: { from: weekStart, to } }),
      summarizeMealLogs({ client: apiClient, query: { from: weekStart, to } }),
      getMyFuel({ client: apiClient, query: { from: weekStart, to } }),
    ]);
    if (logsResult.error || summaryResult.error) {
      setError("Could not load your log.");
      return;
    }
    setError(null);
    setLogs(logsResult.data ?? []);
    setSummary(summaryResult.data ?? []);
    // Targets sit beside the log; when they fail to load, the log still shows.
    setFuel(fuelResult.data?.days ?? []);
  }, [weekStart]);

  useEffect(() => {
    if (user) {
      void reload();
    }
  }, [user, reload]);

  // Recipes and ingredients only make logging quicker: when they fail to load, the form still logs anything by hand.
  useEffect(() => {
    if (!user) {
      return;
    }
    let cancelled = false;
    void Promise.all([
      listMyRecipes({ client: apiClient }),
      listLibraryRecipes({ client: apiClient }),
      listIngredients({ client: apiClient }),
    ]).then(([mine, library, catalog]) => {
      if (!cancelled) {
        setRecipes([...(mine.data ?? []), ...(library.data ?? [])]);
        setIngredients(catalog.data ?? []);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [user]);

  if (!user) {
    return (
      <>
        <PageHeader title="Log" />
        <SignInPrompt>You need to sign in to keep a log.</SignInPrompt>
      </>
    );
  }

  const summaryByDay = new Map((summary ?? []).map((day) => [day.date, day]));
  const targetByDay = new Map(fuel.flatMap((day) => (day.target ? [[day.date, day.target] as const] : [])));
  const dayLogs = (logs ?? []).filter((log) => log.date === selectedDay);
  const body = logBody(what, selectedDay, mealType, servings, nutrition, amount);
  const parsedServings = parseServings(servings);
  const measured = measuredOf(what);
  const parsedAmount = parseAmount(amount.quantity);

  const addLog = async () => {
    if (!body) {
      return;
    }
    const result = await createMealLog({ client: apiClient, body });
    if (result.error) {
      setError("Could not log that meal.");
      return;
    }
    setError(null);
    setWhat(EMPTY_WHAT);
    setServings("1");
    setNutrition(EMPTY_NUTRITION);
    await reload();
  };

  const remove = async (logId: string) => {
    const result = await deleteMealLog({ client: apiClient, path: { logId } });
    if (result.error) {
      setError("Could not delete that entry.");
      return;
    }
    await reload();
  };

  const nutritionField = (field: keyof NutritionFields, label: string) => (
    <label>
      {label}
      <input
        type="number"
        min={0}
        step="any"
        value={nutrition[field]}
        onChange={(e) => setNutrition({ ...nutrition, [field]: e.target.value })}
      />
    </label>
  );

  return (
    <>
      <PageHeader
        eyebrow={`Week of ${dayLabel(weekStart)}`}
        title="Log"
        actions={
          <>
            <button className="button secondary" onClick={() => setWeekStart(addDays(weekStart, -7))}>
              ← Previous
            </button>
            <button className="button secondary" onClick={() => setWeekStart(addDays(weekStart, 7))}>
              Next →
            </button>
          </>
        }
      />

      {error && (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      )}

      {summary === null || logs === null ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="card">
            <h2>This week, per day</h2>
            <table className="totals">
              <thead>
                <tr>
                  <th>Day</th>
                  <th>Calories</th>
                  <th>Protein</th>
                  <th>Carbs</th>
                  <th>Fat</th>
                </tr>
              </thead>
              <tbody>
                {weekDays(weekStart).flatMap((day) => {
                  const target = targetByDay.get(day);
                  const rows = [
                    {
                      key: day,
                      kind: target ? "with-fuel" : "",
                      label: dayLabel(day),
                      cells: nutritionCells(summaryByDay.get(day)),
                    },
                    ...(target
                      ? [{ key: `${day}-fuel`, kind: "fuel-row", label: FUEL_LABEL, cells: nutritionCells(target) }]
                      : []),
                  ];
                  return rows.map((row) => (
                    <tr
                      key={row.key}
                      className={`${row.kind} ${day === selectedDay ? "selected" : ""}`.trim() || undefined}
                      onClick={() => setSelectedDay(day)}
                    >
                      <td>{row.label}</td>
                      {row.cells.map((cell, index) => (
                        <td key={index}>{cell}</td>
                      ))}
                    </tr>
                  ));
                })}
              </tbody>
            </table>
            <p className="muted">Tap a day to see and add its meals.</p>
          </div>

          <div className="card">
            <h2>{dayLabel(selectedDay)}</h2>
            {dayLogs.length === 0 ? (
              <p className="muted">Nothing logged. Plan a meal and tap “Log”, or add one below.</p>
            ) : (
              <ul className="log-entries">
                {dayLogs.map((log) => (
                  <li key={log.id}>
                    <span className="muted">{mealLabel(log.mealType)}</span>{" "}
                    <strong>{log.description}</strong>{" "}
                    <span className="muted">
                      {log.recipeId ? `(${formatServings(log.servings)}) ` : ""}
                      {formatKcal(log.nutrition?.calories)}
                    </span>
                    <button className="button secondary small" onClick={() => void remove(log.id)}>
                      Delete
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <h3>Log something else</h3>
            <div className="field-row">
              <Combobox
                label="What"
                ariaLabel="What"
                value={whatText(what)}
                placeholder="Search recipes and ingredients, or type anything"
                className="log-what"
                options={searchWhat(whatText(what), recipes, ingredients)}
                keyOf={optionKey}
                onType={(text) => setWhat(typeWhat(text))}
                onChoose={(option) => {
                  setWhat(option);
                  if (option.kind === "recipe") {
                    setServings("1");
                  } else {
                    setAmount(defaultAmount(option.entry.basis));
                  }
                }}
                renderOption={(option) =>
                  option.kind === "recipe" ? (
                    <>
                      <span>{option.recipe.title}</span>
                      <span className="muted">{describeRecipe(option.recipe)}</span>
                    </>
                  ) : (
                    <>
                      <span>{option.entry.name}</span>
                      <span className="muted">{describeIngredient(option.entry)}</span>
                    </>
                  )
                }
              />
              <label>
                Meal
                <select value={mealType} onChange={(e) => setMealType(e.target.value as MealTypeValue)}>
                  {MEAL_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {mealLabel(type)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            {what.kind === "recipe" ? (
              <div className="field-row">
                <label className="log-servings">
                  Servings
                  <input
                    type="number"
                    min={0.1}
                    max={100}
                    step={0.1}
                    value={servings}
                    onChange={(e) => setServings(e.target.value)}
                  />
                </label>
                <p className="muted log-total" role="status">
                  {parsedServings === undefined
                    ? "Servings run from 0.1 to 100."
                    : describeTotal(recipeTotal(what.recipe, parsedServings))}
                </p>
              </div>
            ) : measured ? (
              <div className="field-row">
                <label className="log-servings">
                  Amount
                  <input
                    type="number"
                    min={0}
                    step="any"
                    value={amount.quantity}
                    onChange={(e) => setAmount({ ...amount, quantity: e.target.value })}
                  />
                </label>
                <label className="log-unit">
                  Unit
                  <select
                    value={amount.unit}
                    onChange={(e) => setAmount({ ...amount, unit: e.target.value as UnitSymbol })}
                  >
                    {unitsFor(measured.basis).map((unit) => (
                      <option key={unit} value={unit}>
                        {unitLabel(unit)}
                      </option>
                    ))}
                  </select>
                </label>
                <p className="muted log-total" role="status">
                  {parsedAmount === undefined
                    ? "Enter an amount more than 0."
                    : describeTotal(measuredTotal(measured, parsedAmount, amount.unit))}
                </p>
              </div>
            ) : (
              <div className="field-row">
                {nutritionField("calories", "Calories")}
                {nutritionField("proteinG", "Protein g")}
                {nutritionField("carbsG", "Carbs g")}
                {nutritionField("fatG", "Fat g")}
              </div>
            )}
            <p>
              <button className="button" onClick={() => void addLog()} disabled={body === undefined}>
                Log meal
              </button>
            </p>
          </div>
        </>
      )}
    </>
  );
}

function optionKey(option: WhatOption): string {
  return option.kind === "recipe" ? `recipe:${option.recipe.id}` : `ingredient:${option.entry.slug}`;
}
