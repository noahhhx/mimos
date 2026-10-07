"use client";

import { useCallback, useEffect, useState } from "react";

import {
  createMealLog,
  deleteMealLog,
  listLibraryRecipes,
  listMealLogs,
  listMyRecipes,
  summarizeMealLogs,
  type DailyLogSummary,
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
  todayIso,
  weekDays,
  type MealTypeValue,
} from "@/lib/format";
import { describeRecipe, searchRecipes } from "@/lib/ingredient-links";
import {
  EMPTY_NUTRITION,
  EMPTY_WHAT,
  describeTotal,
  logBody,
  parseServings,
  recipeTotal,
  typeWhat,
  whatText,
  type LogWhat,
  type NutritionFields,
} from "@/lib/log-entry";

/**
 * The food diary: per-day calorie and macro totals for the week, the
 * selected day's meals, and a log for the unplanned ones: a recipe, in
 * servings, or anything else counted by hand.
 * Information, not judgement — no streaks, no scolding.
 */
export default function LogPage() {
  const { user } = useAuth();
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [selectedDay, setSelectedDay] = useState(todayIso());
  const [logs, setLogs] = useState<MealLog[] | null>(null);
  const [summary, setSummary] = useState<DailyLogSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [recipes, setRecipes] = useState<RecipeSummary[]>([]);
  const [what, setWhat] = useState<LogWhat>(EMPTY_WHAT);
  const [mealType, setMealType] = useState<MealTypeValue>("SNACK");
  const [servings, setServings] = useState("1");
  const [nutrition, setNutrition] = useState<NutritionFields>(EMPTY_NUTRITION);

  const reload = useCallback(async () => {
    const to = addDays(weekStart, 6);
    const [logsResult, summaryResult] = await Promise.all([
      listMealLogs({ client: apiClient, query: { from: weekStart, to } }),
      summarizeMealLogs({ client: apiClient, query: { from: weekStart, to } }),
    ]);
    if (logsResult.error || summaryResult.error) {
      setError("Could not load your log.");
      return;
    }
    setError(null);
    setLogs(logsResult.data ?? []);
    setSummary(summaryResult.data ?? []);
  }, [weekStart]);

  useEffect(() => {
    if (user) {
      void reload();
    }
  }, [user, reload]);

  // Recipes only make logging quicker: when they fail to load, the form still logs anything by hand.
  useEffect(() => {
    if (!user) {
      return;
    }
    let cancelled = false;
    void Promise.all([listMyRecipes({ client: apiClient }), listLibraryRecipes({ client: apiClient })]).then(
      ([mine, library]) => {
        if (!cancelled) {
          setRecipes([...(mine.data ?? []), ...(library.data ?? [])]);
        }
      },
    );
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
  const dayLogs = (logs ?? []).filter((log) => log.date === selectedDay);
  const body = logBody(what, selectedDay, mealType, servings, nutrition);
  const parsedServings = parseServings(servings);

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
                {weekDays(weekStart).map((day) => {
                  const totals = summaryByDay.get(day);
                  return (
                    <tr
                      key={day}
                      className={day === selectedDay ? "selected" : undefined}
                      onClick={() => setSelectedDay(day)}
                    >
                      <td>{dayLabel(day)}</td>
                      <td>{formatKcal(totals?.calories)}</td>
                      <td>{totals?.proteinG != null ? `${Math.round(totals.proteinG)} g` : "–"}</td>
                      <td>{totals?.carbsG != null ? `${Math.round(totals.carbsG)} g` : "–"}</td>
                      <td>{totals?.fatG != null ? `${Math.round(totals.fatG)} g` : "–"}</td>
                    </tr>
                  );
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
                placeholder="Search your recipes, or type anything"
                className="log-what"
                options={searchRecipes(whatText(what), recipes, 6)}
                keyOf={(recipe) => recipe.id}
                onType={(text) => setWhat(typeWhat(text))}
                onChoose={(recipe) => {
                  setWhat({ kind: "recipe", recipe });
                  setServings("1");
                }}
                renderOption={(recipe) => (
                  <>
                    <span>{recipe.title}</span>
                    <span className="muted">{describeRecipe(recipe)}</span>
                  </>
                )}
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
