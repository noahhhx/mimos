"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import {
  addMealPlanEntry,
  createMealLog,
  deleteMealPlanEntry,
  getHousehold,
  getMealPlan,
  getPlanSuggestions,
  getWeekPanels,
  listLibraryRecipes,
  listMyRecipes,
  updateMealPlanEntry,
  type MealPlan,
  type Person,
  type PlanSuggestion,
  type RecipeSummary,
  type WeekPanel,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { WeekPanelSection } from "@/components/week-panel";
import { apiClient } from "@/lib/api";
import {
  chipLabels,
  defaultServings,
  eatsIt,
  parsePlanView,
  shareOf,
  toggleDiner,
  visibleEntries,
  type PlanView,
} from "@/lib/diners";
import { MEAL_TYPES, addDays, dayLabel, formatServings, mealLabel, mondayOf, weekDays } from "@/lib/format";
import { applySuggestionEntries } from "@/lib/suggestions";

type Entry = MealPlan["entries"][number];

const VIEW_KEY = "mimos.plan.view";

/**
 * The week planner: a day × meal grid fed from both the library and your
 * own recipes. Servings adjust here, and a planned meal you eat can be
 * logged (planned meals become logged meals). In a shared household
 * (ADR-0019) each meal shows who eats it, the week shows your meals or
 * everyone's, and logging a shared meal logs your share.
 */
export default function PlanPage() {
  const { user, signIn } = useAuth();
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [plan, setPlan] = useState<MealPlan | null>(null);
  const [suggestions, setSuggestions] = useState<PlanSuggestion[] | null>(null);
  // Tagged with their week, so a slow answer for a week left behind never shows.
  const [panels, setPanels] = useState<{ week: string; list: WeekPanel[] } | null>(null);
  const [applying, setApplying] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [picker, setPicker] = useState<{ date: string; mealType: string } | null>(null);
  const [pickerQuery, setPickerQuery] = useState("");
  const [pickerResults, setPickerResults] = useState<RecipeSummary[] | null>(null);
  const [logged, setLogged] = useState<string | null>(null);
  const [members, setMembers] = useState<Person[]>([]);
  const [view, setView] = useState<PlanView>("mine");
  const [sharing, setSharing] = useState<{ entryId: string; servings: string } | null>(null);

  useEffect(() => {
    try {
      setView(parsePlanView(window.localStorage.getItem(VIEW_KEY)));
    } catch {
      // Storage can be unavailable (private windows, blocked site data); the default stands.
    }
  }, []);

  useEffect(() => {
    // Honor ?week= from the shopping list's cross-link, in an effect (not
    // useSearchParams) so the page stays statically prerendered.
    const week = new URLSearchParams(window.location.search).get("week");
    if (week && /^\d{4}-\d{2}-\d{2}$/.test(week)) {
      setWeekStart(mondayOf(new Date(`${week}T00:00:00`)));
    }
  }, []);

  // Panels depend on the week, not on its meals, so only a new week loads them.
  const reload = useCallback(
    async (withPanels = false) => {
      const [planResult, suggestionsResult, householdResult, panelsResult] = await Promise.all([
        getMealPlan({ client: apiClient, path: { startDate: weekStart } }),
        getPlanSuggestions({ client: apiClient, path: { startDate: weekStart } }),
        getHousehold({ client: apiClient }),
        withPanels ? getWeekPanels({ client: apiClient, path: { startDate: weekStart } }) : undefined,
      ]);
      // Without the household, the page plans as a household of one would.
      setMembers(householdResult.data?.members ?? []);
      // Panels are an enhancement too (ADR-0017): one that fails to load is hidden.
      if (panelsResult) {
        setPanels({ week: weekStart, list: panelsResult.data?.panels ?? [] });
      }
      if (planResult.error) {
        setError("Could not load the plan.");
        return;
      }
      setError(null);
      setPlan(planResult.data ?? null);
      // Suggestions are an enhancement (ADR-0006): a failure here never
      // hurts the plan itself.
      setSuggestions(suggestionsResult.error ? [] : (suggestionsResult.data?.suggestions ?? []));
    },
    [weekStart],
  );

  // Keyed on the subject, not the User: a silent token renewal replaces the
  // User object, and reloading panels then would drop a wheel's landed spin.
  const subject = user?.profile.sub;
  useEffect(() => {
    if (subject) {
      void reload(true);
    }
  }, [subject, reload]);

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

  // A household of one sees the page as it was before households: no diners, no toggle.
  const shared = members.length > 1;
  const memberLabels = chipLabels(members.map((member) => member.displayName));
  const shown = shared ? visibleEntries(plan?.entries ?? [], view) : (plan?.entries ?? []);
  const entriesBySlot = new Map<string, Entry[]>();
  for (const day of weekDays(weekStart)) {
    for (const mealType of MEAL_TYPES) {
      entriesBySlot.set(
        `${day}:${mealType}`,
        shown.filter((entry) => entry.date === day && entry.mealType === mealType),
      );
    }
  }

  const chooseView = (next: PlanView) => {
    setView(next);
    try {
      window.localStorage.setItem(VIEW_KEY, next);
    } catch {
      // Not remembered, but the view still changes.
    }
  };

  const planMeal = async (recipeId: string) => {
    if (!picker) {
      return;
    }
    const mealType = picker.mealType as (typeof MEAL_TYPES)[number];
    const result = await addMealPlanEntry({
      client: apiClient,
      path: { startDate: weekStart },
      body: { date: picker.date, mealType, recipeId, servings: defaultServings(mealType, members.length) },
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

  const changeDiners = async (entry: Entry, memberId: string) => {
    const diners = toggleDiner(entry.diners.map((diner) => diner.id), memberId);
    if (diners === null) {
      return;
    }
    const result = await updateMealPlanEntry({
      client: apiClient,
      path: { startDate: weekStart, entryId: entry.id },
      body: { diners },
    });
    if (result.error) {
      setError("Could not change who eats that meal.");
      return;
    }
    await reload();
  };

  const unplan = async (entryId: string) => {
    await deleteMealPlanEntry({ client: apiClient, path: { startDate: weekStart, entryId } });
    await reload();
  };

  const logMeal = async (entry: Entry, servings: number) => {
    const result = await createMealLog({
      client: apiClient,
      body: {
        date: entry.date,
        mealType: entry.mealType,
        recipeId: entry.recipeId,
        servings,
      },
    });
    if (result.error) {
      setError("Could not log that meal.");
      return;
    }
    setSharing(null);
    setLogged(`${entry.recipeTitle} logged.`);
  };

  // A meal eaten alone logs in one tap; a shared one asks for your share first.
  const startLog = (entry: Entry) => {
    if (entry.diners.length > 1) {
      setSharing({ entryId: entry.id, servings: String(shareOf(entry.servings, entry.diners.length)) });
    } else {
      void logMeal(entry, entry.servings);
    }
  };

  // A panel's button can change what its plugin suggests (Country of the
  // Week suggests dishes from the chosen country), so suggestions follow.
  const replacePanel = async (panel: WeekPanel) => {
    setPanels(
      (current) =>
        current && {
          ...current,
          list: current.list.map((each) => (each.pluginId === panel.pluginId ? panel : each)),
        },
    );
    const result = await getPlanSuggestions({ client: apiClient, path: { startDate: weekStart } });
    setSuggestions(result.error ? [] : (result.data?.suggestions ?? []));
  };

  const applySuggestion = async (suggestion: PlanSuggestion) => {
    setApplying(true);
    const added = await applySuggestionEntries(weekStart, suggestion.entries);
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

      {panels !== null && panels.week === weekStart && panels.list.length > 0 && (
        <div className="week-panels" aria-label="Plugins for this week" role="group">
          {panels.list.map((panel) => (
            <WeekPanelSection
              key={`${panels.week}:${panel.pluginId}`}
              startDate={panels.week}
              panel={panel}
              onChange={(next) => void replacePanel(next)}
            />
          ))}
        </div>
      )}

      {suggestions !== null && suggestions.length > 0 && (
        <section className="card suggestions" aria-label="Suggestions from plugins">
          <h2>Suggestions</h2>
          <p className="muted">
            From the <Link href="/app/plugins">plugins</Link> you turned on.
          </p>
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
                      {formatServings(entry.servings)}
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

      {shared && (
        <div className="tabs" role="tablist" aria-label="Whose meals">
          <button
            role="tab"
            aria-selected={view === "mine"}
            className={view === "mine" ? "active" : undefined}
            onClick={() => chooseView("mine")}
          >
            Mine
          </button>
          <button
            role="tab"
            aria-selected={view === "everyone"}
            className={view === "everyone" ? "active" : undefined}
            onClick={() => chooseView("everyone")}
          >
            Everyone
          </button>
        </div>
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
                          <span>{formatServings(entry.servings)}</span>
                          <button
                            className="button secondary small"
                            onClick={() => void changeServings(entry.id, entry.servings + 0.5)}
                            aria-label="More servings"
                          >
                            +
                          </button>
                          {eatsIt(entry) && (
                            <button className="button secondary small" onClick={() => startLog(entry)}>
                              Log
                            </button>
                          )}
                          <button
                            className="button secondary small"
                            onClick={() => void unplan(entry.id)}
                            aria-label={`Remove ${entry.recipeTitle}`}
                          >
                            ✕
                          </button>
                        </div>
                        {shared && (
                          <div className="diners" role="group" aria-label="Who eats it">
                            {members.map((member, index) => {
                              const eats = entry.diners.some((diner) => diner.id === member.id);
                              const last = eats && entry.diners.length === 1;
                              return (
                                <button
                                  key={member.id}
                                  className={eats ? "button small" : "button secondary small"}
                                  aria-pressed={eats}
                                  aria-label={member.you ? `${member.displayName} (you)` : member.displayName}
                                  title={last ? `${member.displayName} is the only one eating it` : member.displayName}
                                  disabled={last}
                                  onClick={() => void changeDiners(entry, member.id)}
                                >
                                  {memberLabels[index]}
                                </button>
                              );
                            })}
                          </div>
                        )}
                        {sharing?.entryId === entry.id && (
                          <form
                            className="share"
                            aria-label={`Log your share of ${entry.recipeTitle}`}
                            onSubmit={(event) => {
                              event.preventDefault();
                              void logMeal(entry, Number(sharing.servings));
                            }}
                          >
                            <label>
                              Your share
                              <input
                                type="number"
                                min={0.1}
                                max={100}
                                step={0.1}
                                required
                                value={sharing.servings}
                                onChange={(event) => setSharing({ entryId: entry.id, servings: event.target.value })}
                              />
                            </label>
                            <span className="muted">
                              of {formatServings(entry.servings)} for {entry.diners.length} people
                            </span>
                            <button className="button small" type="submit">
                              Log share
                            </button>
                            <button className="button secondary small" type="button" onClick={() => setSharing(null)}>
                              Cancel
                            </button>
                          </form>
                        )}
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
                      · {formatServings(recipe.servings)}
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
