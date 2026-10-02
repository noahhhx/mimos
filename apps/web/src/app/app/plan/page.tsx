"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import {
  addMealPlanEntry,
  createMealLog,
  deleteMealPlanEntry,
  getMealPlan,
  getPlanSuggestions,
  listLibraryRecipes,
  listMyRecipes,
  updateMealPlanEntry,
  type MealPlan,
  type PlanSuggestion,
  type RecipeSummary,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { apiClient } from "@/lib/api";
import { MEAL_TYPES, addDays, dayLabel, mealLabel, mondayOf, weekDays } from "@/lib/format";

/**
 * The week planner: a day × meal grid fed from both the library and your
 * own recipes. Servings adjust here; each planned meal can be logged with
 * one tap (planned meals become logged meals).
 */
export default function PlanPage() {
  const { user, signIn } = useAuth();
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [plan, setPlan] = useState<MealPlan | null>(null);
  const [suggestions, setSuggestions] = useState<PlanSuggestion[] | null>(null);
  const [applying, setApplying] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [picker, setPicker] = useState<{ date: string; mealType: string } | null>(null);
  const [pickerQuery, setPickerQuery] = useState("");
  const [pickerResults, setPickerResults] = useState<RecipeSummary[] | null>(null);
  const [logged, setLogged] = useState<string | null>(null);

  useEffect(() => {
    // Honor ?week= from the shopping list's cross-link, in an effect (not
    // useSearchParams) so the page stays statically prerendered.
    const week = new URLSearchParams(window.location.search).get("week");
    if (week && /^\d{4}-\d{2}-\d{2}$/.test(week)) {
      setWeekStart(mondayOf(new Date(`${week}T00:00:00`)));
    }
  }, []);

  const reload = useCallback(async () => {
    const [planResult, suggestionsResult] = await Promise.all([
      getMealPlan({ client: apiClient, path: { startDate: weekStart } }),
      getPlanSuggestions({ client: apiClient, path: { startDate: weekStart } }),
    ]);
    if (planResult.error) {
      setError("Could not load the plan.");
      return;
    }
    setError(null);
    setPlan(planResult.data ?? null);
    // Suggestions are an enhancement (ADR-0006): a failure here never
    // hurts the plan itself.
    setSuggestions(suggestionsResult.error ? [] : (suggestionsResult.data?.suggestions ?? []));
  }, [weekStart]);

  useEffect(() => {
    if (user) {
      void reload();
    }
  }, [user, reload]);

  useEffect(() => {
    if (!picker) {
      return;
    }
    let cancelled = false;
    void (async () => {
      const query = pickerQuery ? { q: pickerQuery } : {};
      const [mine, library] = await Promise.all([
        listMyRecipes({ client: apiClient, query }),
        listLibraryRecipes({ client: apiClient, query }),
      ]);
      if (cancelled) {
        return;
      }
      setPickerResults([...(mine.data ?? []), ...(library.data ?? [])]);
    })();
    return () => {
      cancelled = true;
    };
  }, [picker, pickerQuery]);

  if (!user) {
    return (
      <>
        <PageHeader title="Plan" />
        <p>You need to sign in to plan meals.</p>
        <button className="button" onClick={() => void signIn()}>
          Sign in
        </button>
      </>
    );
  }

  const entriesBySlot = new Map<string, MealPlan["entries"]>();
  for (const day of weekDays(weekStart)) {
    for (const mealType of MEAL_TYPES) {
      entriesBySlot.set(
        `${day}:${mealType}`,
        (plan?.entries ?? []).filter((entry) => entry.date === day && entry.mealType === mealType),
      );
    }
  }

  const planMeal = async (recipeId: string) => {
    if (!picker) {
      return;
    }
    const result = await addMealPlanEntry({
      client: apiClient,
      path: { startDate: weekStart },
      body: { date: picker.date, mealType: picker.mealType as (typeof MEAL_TYPES)[number], recipeId, servings: 2 },
    });
    if (result.error) {
      setError("Could not plan that meal.");
      return;
    }
    setPicker(null);
    setPickerQuery("");
    setPickerResults(null);
    await reload();
  };

  const changeServings = async (entryId: string, servings: number) => {
    const next = Math.round(servings * 2) / 2;
    if (next < 0.5 || next > 100) {
      return;
    }
    await updateMealPlanEntry({ client: apiClient, path: { startDate: weekStart, entryId }, body: { servings: next } });
    await reload();
  };

  const unplan = async (entryId: string) => {
    await deleteMealPlanEntry({ client: apiClient, path: { startDate: weekStart, entryId } });
    await reload();
  };

  const logMeal = async (entry: MealPlan["entries"][number]) => {
    const result = await createMealLog({
      client: apiClient,
      body: {
        date: entry.date,
        mealType: entry.mealType,
        recipeId: entry.recipeId,
        servings: entry.servings,
      },
    });
    if (result.error) {
      setError("Could not log that meal.");
      return;
    }
    setLogged(`${entry.recipeTitle} logged.`);
  };

  // Applying a card reuses the plan-entry endpoint — the same one the
  // picker uses; plugins have no write path of their own (ADR-0006).
  const applySuggestion = async (suggestion: PlanSuggestion) => {
    setApplying(true);
    let added = 0;
    for (const entry of suggestion.entries) {
      const result = await addMealPlanEntry({
        client: apiClient,
        path: { startDate: weekStart },
        body: {
          date: entry.date,
          mealType: entry.mealType,
          recipeId: entry.recipeId,
          servings: entry.servings,
        },
      });
      if (!result.error) {
        added++;
      }
    }
    setApplying(false);
    setNotice(
      added > 0 ? `Added ${added} meal${added === 1 ? "" : "s"} from "${suggestion.title}".` : "Could not add that suggestion.",
    );
    await reload();
  };

  return (
    <>
      <PageHeader
        eyebrow={`Week of ${dayLabel(weekStart)}`}
        title="Plan"
        actions={
          <>
            <button className="button secondary" onClick={() => setWeekStart(addDays(weekStart, -7))}>
              ← Previous
            </button>
            <button className="button secondary" onClick={() => setWeekStart(addDays(weekStart, 7))}>
              Next →
            </button>
            <Link className="button secondary" href={`/app/shopping-list?week=${weekStart}`}>
              Shopping list
            </Link>
          </>
        }
      />

      {error && (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      )}
      {logged && (
        <div className="card ok" role="status">
          <p>{logged}</p>
        </div>
      )}
      {notice && (
        <div className="card ok" role="status">
          <p>{notice}</p>
        </div>
      )}

      {suggestions !== null && suggestions.length > 0 && (
        <section className="card suggestions" aria-label="Suggestions from plugins">
          <h2>Suggestions</h2>
          <p className="muted">From plugins enabled on this Mimos instance.</p>
          <div className="suggestion-cards">
            {suggestions.map((suggestion) => (
              <article key={`${suggestion.pluginId}:${suggestion.title}`} className="suggestion-card">
                <div className="suggestion-head">
                  {suggestion.icon && (
                    <span className="suggestion-icon" aria-hidden="true">
                      {suggestion.icon}
                    </span>
                  )}
                  <h3>{suggestion.title}</h3>
                </div>
                {suggestion.blurb && <p>{suggestion.blurb}</p>}
                <ul>
                  {suggestion.entries.map((entry) => (
                    <li key={`${entry.date}:${entry.mealType}:${entry.recipeId}`}>
                      {entry.recipeTitle} · {dayLabel(entry.date)} {mealLabel(entry.mealType)} ·{" "}
                      {entry.servings} servings
                    </li>
                  ))}
                </ul>
                <div className="suggestion-actions">
                  <button
                    className="button"
                    disabled={applying}
                    onClick={() => void applySuggestion(suggestion)}
                  >
                    {applying ? "Adding…" : "Add to plan"}
                  </button>
                  <span className="muted">via {suggestion.pluginName}</span>
                </div>
              </article>
            ))}
          </div>
        </section>
      )}

      {plan === null ? (
        <p className="muted">Loading…</p>
      ) : (
        <div className="week">
          {weekDays(weekStart).map((day) => (
            <section key={day} className="card day">
              <h2>{dayLabel(day)}</h2>
              {MEAL_TYPES.map((mealType) => {
                const entries = entriesBySlot.get(`${day}:${mealType}`) ?? [];
                return (
                  <div key={mealType} className="meal-slot">
                    <div className="meal-slot-head">
                      <span className="muted">{mealLabel(mealType)}</span>
                      <button
                        className="button secondary small"
                        onClick={() => setPicker({ date: day, mealType })}
                        aria-label={`Add ${mealLabel(mealType)} for ${dayLabel(day)}`}
                      >
                        +
                      </button>
                    </div>
                    {entries.map((entry) => (
                      <div key={entry.id} className="planned-meal">
                        <Link href={`/app/recipes/${entry.recipeId}`}>{entry.recipeTitle}</Link>
                        <div className="meal-controls">
                          <button
                            className="button secondary small"
                            onClick={() => void changeServings(entry.id, entry.servings - 0.5)}
                            aria-label="Fewer servings"
                          >
                            −
                          </button>
                          <span>{entry.servings} servings</span>
                          <button
                            className="button secondary small"
                            onClick={() => void changeServings(entry.id, entry.servings + 0.5)}
                            aria-label="More servings"
                          >
                            +
                          </button>
                          <button className="button secondary small" onClick={() => void logMeal(entry)}>
                            Log
                          </button>
                          <button
                            className="button secondary small"
                            onClick={() => void unplan(entry.id)}
                            aria-label={`Remove ${entry.recipeTitle}`}
                          >
                            ✕
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                );
              })}
            </section>
          ))}
        </div>
      )}

      {picker && (
        <div className="picker card" role="dialog" aria-label="Pick a recipe">
          <div className="toolbar">
            <h2>
              {mealLabel(picker.mealType)} · {dayLabel(picker.date)}
            </h2>
            <button className="button secondary" onClick={() => setPicker(null)}>
              Close
            </button>
          </div>
          <input
            type="search"
            value={pickerQuery}
            onChange={(e) => setPickerQuery(e.target.value)}
            placeholder="Search your recipes and the library…"
            autoFocus
          />
          {pickerResults === null ? (
            <p className="muted">Searching…</p>
          ) : pickerResults.length === 0 ? (
            <p className="muted">Nothing matches.</p>
          ) : (
            <ul className="picker-results">
              {pickerResults.map((recipe) => (
                <li key={recipe.id}>
                  <button className="button secondary" onClick={() => void planMeal(recipe.id)}>
                    {recipe.title}
                    <span className="muted">
                      {" "}
                      · {recipe.servings} servings
                      {recipe.isLibrary ? " · library" : ""}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </>
  );
}
