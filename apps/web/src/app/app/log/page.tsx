"use client";

import { useCallback, useEffect, useState } from "react";

import {
  createMealLog,
  deleteMealLog,
  listMealLogs,
  summarizeMealLogs,
  type DailyLogSummary,
  type MealLog,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { apiClient } from "@/lib/api";
import { MEAL_TYPES, addDays, dayLabel, formatKcal, formatServings, mealLabel, mondayOf, todayIso, weekDays } from "@/lib/format";

/**
 * The food diary: per-day calorie and macro totals for the week, the
 * selected day's meals, and an ad-hoc log for the unplanned ones.
 * Information, not judgement — no streaks, no scolding.
 */
export default function LogPage() {
  const { user, signIn } = useAuth();
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [selectedDay, setSelectedDay] = useState(todayIso());
  const [logs, setLogs] = useState<MealLog[] | null>(null);
  const [summary, setSummary] = useState<DailyLogSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [description, setDescription] = useState("");
  const [mealType, setMealType] = useState<string>("SNACK");
  const [calories, setCalories] = useState("");
  const [proteinG, setProteinG] = useState("");
  const [carbsG, setCarbsG] = useState("");
  const [fatG, setFatG] = useState("");

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

  if (!user) {
    return (
      <>
        <PageHeader title="Log" />
        <p>You need to sign in to keep a log.</p>
        <button className="button" onClick={() => void signIn()}>
          Sign in
        </button>
      </>
    );
  }

  const summaryByDay = new Map((summary ?? []).map((day) => [day.date, day]));
  const dayLogs = (logs ?? []).filter((log) => log.date === selectedDay);

  const addAdHoc = async () => {
    const numberOrNull = (value: string) => (value.trim() === "" ? undefined : Number(value));
    const result = await createMealLog({
      client: apiClient,
      body: {
        date: selectedDay,
        mealType: mealType as (typeof MEAL_TYPES)[number],
        description,
        servings: 1,
        nutrition: {
          calories: numberOrNull(calories),
          proteinG: numberOrNull(proteinG),
          carbsG: numberOrNull(carbsG),
          fatG: numberOrNull(fatG),
        },
      },
    });
    if (result.error) {
      setError("Could not log that meal.");
      return;
    }
    setError(null);
    setDescription("");
    setCalories("");
    setProteinG("");
    setCarbsG("");
    setFatG("");
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
              <label>
                What
                <input
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Toast with peanut butter"
                  maxLength={200}
                />
              </label>
              <label>
                Meal
                <select value={mealType} onChange={(e) => setMealType(e.target.value)}>
                  {MEAL_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {mealLabel(type)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <div className="field-row">
              <label>
                Calories
                <input type="number" min={0} step="any" value={calories} onChange={(e) => setCalories(e.target.value)} />
              </label>
              <label>
                Protein g
                <input type="number" min={0} step="any" value={proteinG} onChange={(e) => setProteinG(e.target.value)} />
              </label>
              <label>
                Carbs g
                <input type="number" min={0} step="any" value={carbsG} onChange={(e) => setCarbsG(e.target.value)} />
              </label>
              <label>
                Fat g
                <input type="number" min={0} step="any" value={fatG} onChange={(e) => setFatG(e.target.value)} />
              </label>
            </div>
            <p>
              <button
                className="button"
                onClick={() => void addAdHoc()}
                disabled={description.trim() === ""}
              >
                Log meal
              </button>
            </p>
          </div>
        </>
      )}
    </>
  );
}
