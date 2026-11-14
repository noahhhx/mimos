"use client";

import Link from "next/link";
import { Fragment, useCallback, useEffect, useState } from "react";

import {
  addMealPlanEntry,
  addMyTraining,
  createIngredient,
  createMealLog,
  deleteMealPlanEntry,
  deleteMyTraining,
  getHousehold,
  getMealPlan,
  getMyFuel,
  getPlanSuggestions,
  getWeekPanels,
  listIngredients,
  listLibraryRecipes,
  listMyRecipes,
  updateMealPlanEntry,
  type CatalogIngredient,
  type FoodProduct,
  type FuelDay,
  type FuelTarget,
  type MealPlan,
  type MealPlanEntryInput,
  type Person,
  type PlanSuggestion,
  type PlannedDayTotal,
  type RecipeSummary,
  type TrainingIntensity,
  type TrainingSession,
  type TrainingSport,
  type WeekPanel,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { NewIngredient } from "@/components/new-ingredient";
import { PageHeader } from "@/components/page-header";
import { ScanBarcode } from "@/components/scan-barcode";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { WeekPanelSection } from "@/components/week-panel";
import { apiClient } from "@/lib/api";
import { searchCatalog } from "@/lib/catalog-match";
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
import {
  FUEL_LABEL,
  INTENSITIES,
  SPORTS,
  TRAINING_FAILURES,
  TRAINING_LABEL,
  addTrainingLabelFor,
  fuelLabelFor,
  fuelTargetParts,
  removeSessionLabel,
  sessionParts,
  trainingLabelFor,
} from "@/lib/fuel";
import { TRAINING_FIELD_ERRORS, namedFields } from "@/lib/fuel-fields";
import { describeIngredient, productName, unitLabel } from "@/lib/log-entry";
import {
  amountText,
  entryName,
  existingIngredient,
  foodLogBody,
  newFoodEntry,
  MAX_FOOD_AMOUNT,
  parseFoodAmount,
  plannedWhat,
  productIngredient,
  unitOf,
} from "@/lib/planned-item";
import { plannedTotalParts, showsPlannedTotals, withoutNutritionNote } from "@/lib/planned-totals";
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
  const { user } = useAuth();
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [plan, setPlan] = useState<MealPlan | null>(null);
  // The caller's own fuel for each day: what they plan to eat, and their target while targets are on.
  const [fuel, setFuel] = useState<{ week: string; days: FuelDay[] } | null>(null);
  const [suggestions, setSuggestions] = useState<PlanSuggestion[] | null>(null);
  // Tagged with their week, so a slow answer for a week left behind never shows.
  const [panels, setPanels] = useState<{ week: string; list: WeekPanel[] } | null>(null);
  const [applying, setApplying] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [picker, setPicker] = useState<{ date: string; mealType: (typeof MEAL_TYPES)[number] } | null>(null);
  const [pickerQuery, setPickerQuery] = useState("");
  const [pickerResults, setPickerResults] = useState<RecipeSummary[] | null>(null);
  // Ingredients plan as foods and give a planned food its nutrition when it is logged (ADR-0026).
  const [ingredients, setIngredients] = useState<CatalogIngredient[]>([]);
  // A scanned product that lacks a value, until the person fills it in.
  const [unfinished, setUnfinished] = useState<FoodProduct | null>(null);
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
      const [planResult, fuelResult, suggestionsResult, householdResult, panelsResult] = await Promise.all([
        getMealPlan({ client: apiClient, path: { startDate: weekStart } }),
        getMyFuel({ client: apiClient, query: { from: weekStart, to: addDays(weekStart, 6) } }),
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
      setFuel(fuelResult.data ? { week: weekStart, days: fuelResult.data.days } : null);
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

  // Foods only make planning quicker: when the catalog fails to load, recipes plan as before.
  useEffect(() => {
    if (!subject) {
      return;
    }
    let cancelled = false;
    void listIngredients({ client: apiClient }).then((result) => {
      if (!cancelled) {
        setIngredients(result.data ?? []);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [subject]);

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
        <SignInPrompt>You need to sign in to plan meals.</SignInPrompt>
      </>
    );
  }

  // A household of one sees the page as it was before households: no diners, no toggle.
  const shared = members.length > 1;
  const memberLabels = chipLabels(members.map((member) => member.displayName));
  const shown = shared ? visibleEntries(plan?.entries ?? [], view) : (plan?.entries ?? []);
  const fuelByDay = new Map(
    fuel !== null && fuel.week === weekStart && showsPlannedTotals(shared, view)
      ? fuel.days.map((day) => [day.date, day])
      : [],
  );
  const entriesBySlot = new Map<string, Entry[]>();
  for (const day of weekDays(weekStart)) {
    for (const mealType of MEAL_TYPES) {
      entriesBySlot.set(
        `${day}:${mealType}`,
        shown.filter((entry) => entry.date === day && entry.mealType === mealType),
      );
    }
  }

  const foodResults = searchCatalog(pickerQuery, ingredients);

  const chooseView = (next: PlanView) => {
    setView(next);
    try {
      window.localStorage.setItem(VIEW_KEY, next);
    } catch {
      // Not remembered, but the view still changes.
    }
  };

  const closePicker = () => {
    setPicker(null);
    setPickerQuery("");
    setPickerResults(null);
    setUnfinished(null);
  };

  const planItem = async (item: Pick<MealPlanEntryInput, "recipeId" | "food" | "servings">) => {
    if (!picker) {
      return;
    }
    const result = await addMealPlanEntry({
      client: apiClient,
      path: { startDate: weekStart },
      body: { date: picker.date, mealType: picker.mealType, ...item },
    });
    if (result.error) {
      setError("Could not plan that meal.");
      return;
    }
    closePicker();
    await reload();
  };

  const planRecipe = (recipeId: string) =>
    picker && planItem({ recipeId, servings: defaultServings(picker.mealType, members.length) });

  const planFood = (food: CatalogIngredient, servingSize?: number) =>
    picker &&
    planItem(newFoodEntry({ slug: food.slug, basis: food.basis, servingSize }, picker.mealType, members.length));

  // A product with all four values becomes one of the household's ingredients, once, and is planned.
  const planProduct = async (product: FoodProduct) => {
    const input = productIngredient(product);
    if (!input) {
      setUnfinished(product);
      return;
    }
    let food = existingIngredient(input, ingredients);
    if (!food) {
      const created = await createIngredient({ client: apiClient, body: input });
      if (!created.data) {
        setError("Could not add that product.");
        return;
      }
      food = created.data;
      setIngredients((current) => [...current, created.data]);
    }
    await planFood(food, product.servingSize);
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

  const changeAmount = async (entryId: string, amount: number) => {
    const result = await updateMealPlanEntry({
      client: apiClient,
      path: { startDate: weekStart, entryId },
      body: { servings: amount },
    });
    if (result.error) {
      setError("Could not change that amount.");
    }
    await reload();
  };

  // A recipe logs as servings of it; a food logs as an ad-hoc meal of its amount, with the nutrition calculated here.
  const logMeal = async (entry: Entry, amount: number) => {
    const what = plannedWhat(entry);
    const body =
      what.kind === "recipe"
        ? { date: entry.date, mealType: entry.mealType, recipeId: what.recipeId, servings: amount }
        : foodLogBody(entry, ingredients, amount);
    const result = body && (await createMealLog({ client: apiClient, body }));
    if (!result || result.error) {
      setError("Could not log that meal.");
      return;
    }
    setSharing(null);
    setLogged(`${entryName(entry)} logged.`);
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
              {fuelByDay.get(day)?.target && (
                <TrainingSlot date={day} sessions={fuelByDay.get(day)?.sessions ?? []} onChange={() => reload()} />
              )}
              <FuelLine date={day} target={fuelByDay.get(day)?.target} />
              <PlannedTotal day={fuelByDay.get(day)?.planned} />
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
                    {entries.map((entry) => {
                      const what = plannedWhat(entry);
                      return (
                      <div key={entry.id} className="planned-meal">
                        {what.kind === "recipe" ? (
                          <Link href={`/app/recipes/${what.recipeId}`}>{what.title}</Link>
                        ) : (
                          <span className="planned-name">{what.food.name}</span>
                        )}
                        <div className="meal-controls">
                          {what.kind === "recipe" ? (
                            <>
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
                            </>
                          ) : (
                            <FoodAmount
                              name={what.food.name}
                              amount={entry.servings}
                              unit={what.food.unit}
                              onSave={(amount) => changeAmount(entry.id, amount)}
                            />
                          )}
                          {eatsIt(entry) && (
                            <button className="button secondary small" onClick={() => startLog(entry)}>
                              Log
                            </button>
                          )}
                          <button
                            className="button secondary small"
                            onClick={() => void unplan(entry.id)}
                            aria-label={`Remove ${entryName(entry)}`}
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
                            aria-label={`Log your share of ${entryName(entry)}`}
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
                                max={what.kind === "recipe" ? 100 : 10_000}
                                step={what.kind === "recipe" ? 0.1 : "any"}
                                required
                                value={sharing.servings}
                                onChange={(event) => setSharing({ entryId: entry.id, servings: event.target.value })}
                              />
                            </label>
                            <span className="muted">
                              of {amountText(entry)} for {entry.diners.length} people
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
                      );
                    })}
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
            <button className="button secondary" onClick={closePicker}>
              Close
            </button>
          </div>
          <div className="picker-search">
            <input
              type="search"
              value={pickerQuery}
              onChange={(e) => setPickerQuery(e.target.value)}
              placeholder="Search recipes and ingredients…"
              aria-label="Search recipes and ingredients"
              autoFocus
            />
            <ScanBarcode onFound={(product) => void planProduct(product)} />
          </div>
          {unfinished ? (
            <NewIngredient
              initialName={productName(unfinished)}
              initialBasis={unfinished.basis}
              initialNutrition={unfinished.nutrition}
              onCreated={(created) => {
                setIngredients((current) => [...current, created]);
                void planFood(created, created.basis === unfinished.basis ? unfinished.servingSize : undefined);
              }}
              onCancel={() => setUnfinished(null)}
            />
          ) : pickerResults === null ? (
            <p className="muted">Searching…</p>
          ) : pickerResults.length === 0 && foodResults.length === 0 ? (
            <p className="muted">Nothing matches.</p>
          ) : (
            <ul className="picker-results">
              {pickerResults.map((recipe) => (
                <li key={recipe.id}>
                  <button className="button secondary" onClick={() => void planRecipe(recipe.id)}>
                    {recipe.title}
                    <span className="muted">
                      {" "}
                      · {formatServings(recipe.servings)}
                      {recipe.isLibrary ? " · library" : ""}
                    </span>
                  </button>
                </li>
              ))}
              {foodResults.map((food) => (
                <li key={food.slug}>
                  <button className="button secondary" onClick={() => void planFood(food)}>
                    {food.name}
                    <span className="muted"> · {describeIngredient(food)}</span>
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

/**
 * A planned food's amount: a number with its unit beside it, saved on Enter
 * or when focus leaves. Anything the API would refuse goes back to what is
 * planned.
 */
function FoodAmount({
  name,
  amount,
  unit,
  onSave,
}: {
  name: string;
  amount: number;
  unit: string | undefined;
  onSave: (amount: number) => Promise<void>;
}) {
  const [text, setText] = useState(String(amount));
  const [seen, setSeen] = useState(amount);
  if (seen !== amount) {
    setSeen(amount);
    setText(String(amount));
  }

  const commit = () => {
    const parsed = parseFoodAmount(text);
    if (parsed === undefined || parsed === amount) {
      setText(String(amount));
      return;
    }
    void onSave(parsed);
  };

  return (
    <label className="food-amount">
      <input
        type="number"
        inputMode="decimal"
        min={0}
        max={MAX_FOOD_AMOUNT}
        step="any"
        aria-label={`Amount of ${name}`}
        value={text}
        onChange={(event) => setText(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === "Enter") {
            event.preventDefault();
            commit();
          }
        }}
      />
      <span>{unitLabel(unitOf({ unit }), Number(text))}</span>
    </label>
  );
}

function PlannedTotal({ day }: { day: PlannedDayTotal | undefined }) {
  if (!day || day.meals === 0) {
    return null;
  }
  const parts = plannedTotalParts(day.total);
  const note = withoutNutritionNote(day);
  return (
    <div className="meal-slot" role="group" aria-label={`Planned for ${dayLabel(day.date)}`}>
      <div className="meal-slot-head">
        <span className="muted">Planned</span>
      </div>
      {parts.length > 0 && <NutritionParts className="planned-total" parts={parts} />}
      {note && <p className="muted">{note}</p>}
    </div>
  );
}

type SessionDraft = { sport: TrainingSport; minutes: string; intensity: TrainingIntensity };

/**
 * A day's training while its fuel shows: each session, a way to remove one
 * entered by hand, and a form to add one. Every change reloads the week, so
 * the day's target follows at once.
 */
function TrainingSlot({
  date,
  sessions,
  onChange,
}: {
  date: string;
  sessions: TrainingSession[];
  onChange: () => Promise<void>;
}) {
  const [draft, setDraft] = useState<SessionDraft | null>(null);
  const [saving, setSaving] = useState(false);
  const [problem, setProblem] = useState<"minutes" | keyof typeof TRAINING_FAILURES | null>(null);
  const minutesError = `training-${date}-minutes-error`;

  const edit = (patch: Partial<SessionDraft>) => {
    setDraft((current) => current && { ...current, ...patch });
    setProblem(null);
  };

  const add = async (session: SessionDraft) => {
    const minutes = Number(session.minutes);
    if (session.minutes.trim() === "" || !Number.isInteger(minutes)) {
      setProblem("minutes");
      return;
    }
    setSaving(true);
    const result = await addMyTraining({
      client: apiClient,
      body: { date, sport: session.sport, durationMinutes: minutes, intensity: session.intensity },
    });
    setSaving(false);
    if (result.error) {
      const named = namedFields((result.error as { detail?: string }).detail, TRAINING_FIELD_ERRORS);
      setProblem(named.length > 0 ? "minutes" : "add");
      return;
    }
    setDraft(null);
    await onChange();
  };

  const remove = async (session: TrainingSession) => {
    const result = await deleteMyTraining({ client: apiClient, path: { sessionId: session.id } });
    if (result.error) {
      setProblem("remove");
      return;
    }
    setProblem(null);
    await onChange();
  };

  return (
    <div className="meal-slot" role="group" aria-label={trainingLabelFor(date)}>
      <div className="meal-slot-head">
        <span className="muted">{TRAINING_LABEL}</span>
        <button
          className="button secondary small"
          onClick={() => {
            setDraft({ sport: "RIDE", minutes: "", intensity: "ENDURANCE" });
            setProblem(null);
          }}
          aria-label={addTrainingLabelFor(date)}
        >
          +
        </button>
      </div>
      {sessions.map((session) => (
        <div key={session.id} className="training-session">
          <NutritionParts className="training-parts" parts={sessionParts(session)} />
          {session.source === "MANUAL" && (
            <button
              className="button secondary small"
              onClick={() => void remove(session)}
              aria-label={removeSessionLabel(session)}
            >
              ✕
            </button>
          )}
        </div>
      ))}
      {problem !== null && problem !== "minutes" && (
        <p className="error-text" role="alert">
          {TRAINING_FAILURES[problem]}
        </p>
      )}
      {draft && (
        <form
          className="training-form"
          aria-label={addTrainingLabelFor(date)}
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void add(draft);
          }}
        >
          <label>
            Sport
            <select value={draft.sport} onChange={(event) => edit({ sport: event.target.value as TrainingSport })}>
              {Object.entries(SPORTS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <div>
            <label>
              Minutes
              <input
                type="number"
                inputMode="numeric"
                min={1}
                max={1440}
                step={1}
                value={draft.minutes}
                onChange={(event) => edit({ minutes: event.target.value })}
                aria-invalid={problem === "minutes" || undefined}
                aria-describedby={problem === "minutes" ? minutesError : undefined}
              />
            </label>
            {problem === "minutes" && (
              <p className="error-text" id={minutesError}>
                {TRAINING_FIELD_ERRORS.durationMinutes}
              </p>
            )}
          </div>
          <label>
            Intensity
            <select
              value={draft.intensity}
              onChange={(event) => edit({ intensity: event.target.value as TrainingIntensity })}
            >
              {Object.entries(INTENSITIES).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <div className="training-form-actions">
            <button className="button small" type="submit" disabled={saving}>
              {saving ? "Adding…" : "Add"}
            </button>
            <button
              className="button secondary small"
              type="button"
              onClick={() => {
                setDraft(null);
                setProblem(null);
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

function FuelLine({ date, target }: { date: string; target: FuelTarget | undefined }) {
  const parts = fuelTargetParts(target);
  if (parts.length === 0) {
    return null;
  }
  return (
    <div className="meal-slot" role="group" aria-label={fuelLabelFor(date)}>
      <div className="meal-slot-head">
        <span className="muted">{FUEL_LABEL}</span>
      </div>
      <NutritionParts className="fuel-target" parts={parts} />
    </div>
  );
}

/** Each value keeps its unit and label on one line, so a narrow card wraps between values only. */
function NutritionParts({ className, parts }: { className: string; parts: string[] }) {
  return (
    <p className={className}>
      {parts.map((part, index) => (
        <Fragment key={part}>
          {index > 0 && " "}
          <span>
            {part}
            {index < parts.length - 1 && " ·"}
          </span>
        </Fragment>
      ))}
    </p>
  );
}
