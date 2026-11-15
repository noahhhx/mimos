"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

import {
  addMealPlanEntry,
  createIngredient,
  createMealLog,
  deleteMealPlanEntry,
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
  type MealPlan,
  type MealPlanEntryInput,
  type Person,
  type PlanSuggestion,
  type RecipeSummary,
  type WeekPanel,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { DayFuel } from "@/components/day-fuel";
import { FoodAmount } from "@/components/food-amount";
import { NewIngredient } from "@/components/new-ingredient";
import { PageHeader } from "@/components/page-header";
import { ScanBarcode } from "@/components/scan-barcode";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { TrainingSlot } from "@/components/training-slot";
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
import {
  MEAL_TYPES,
  addDays,
  dayLabel,
  formatServings,
  mealLabel,
  mondayOf,
  todayIso,
  weekDays,
  weekdayName,
} from "@/lib/format";
import { TRAINING_LABEL, trainingLabelFor } from "@/lib/fuel";
import { describeIngredient, productName } from "@/lib/log-entry";
import {
  dayHead,
  ledgerFuel,
  ledgerFuelLabel,
  sessionShort,
  standingSelection,
  toggleSelection,
  type LedgerSelection,
} from "@/lib/plan-ledger";
import {
  amountText,
  entryName,
  existingIngredient,
  foodLogBody,
  newFoodEntry,
  plannedWhat,
  productIngredient,
} from "@/lib/planned-item";
import { showsPlannedTotals } from "@/lib/planned-totals";
import { applySuggestionEntries } from "@/lib/suggestions";

type Entry = MealPlan["entries"][number];

const VIEW_KEY = "mimos.plan.view";
const INSPECTOR_TITLE = "plan-inspector-title";

/**
 * The week planner, laid out as a ledger: days across, meals down, and
 * cells that show only what is planned. Selecting a planned meal or a day
 * opens its controls in the inspector beside the table (a sheet on narrow
 * screens): servings or amount, who eats it, Log, and Remove for a meal;
 * training and fuel for a day. Plugins' panels and suggestions sit in the
 * same rail, under the inspector. In a shared household (ADR-0019) each
 * meal shows who eats it, the week shows your meals or everyone's, and
 * logging a shared meal logs your share.
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
  const [logged, setLogged] = useState<{ entryId: string; text: string } | null>(null);
  const [members, setMembers] = useState<Person[]>([]);
  const [view, setView] = useState<PlanView>("mine");
  const [sharing, setSharing] = useState<{ entryId: string; servings: string } | null>(null);
  const [selection, setSelection] = useState<LedgerSelection>(null);
  // Where focus goes back to when the inspector closes: the cell that opened it.
  const opener = useRef<HTMLElement | null>(null);
  const inspectorTitle = useRef<HTMLHeadingElement | null>(null);

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

  // A new selection moves focus to the inspector's title, so a keyboard or
  // screen reader user lands on what they opened (and a phone shows the sheet).
  const selectionKey = selection === null ? null : selection.kind === "entry" ? selection.id : selection.date;
  useEffect(() => {
    if (selectionKey !== null) {
      inspectorTitle.current?.focus();
    }
  }, [selectionKey]);

  const closeInspector = useCallback(() => {
    setSelection(null);
    setSharing(null);
    opener.current?.focus();
    opener.current = null;
  }, []);

  useEffect(() => {
    if (selectionKey === null) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        closeInspector();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [selectionKey, closeInspector]);

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
  const days = weekDays(weekStart);
  const today = todayIso();
  const fuelByDay = new Map(
    fuel !== null && fuel.week === weekStart && showsPlannedTotals(shared, view)
      ? fuel.days.map((day) => [day.date, day])
      : [],
  );
  const targetsOn = [...fuelByDay.values()].some((day) => day.target !== undefined);
  const entriesBySlot = new Map<string, Entry[]>();
  for (const day of days) {
    for (const mealType of MEAL_TYPES) {
      entriesBySlot.set(
        `${day}:${mealType}`,
        shown.filter((entry) => entry.date === day && entry.mealType === mealType),
      );
    }
  }

  // What is selected, if it is still there: a removed or hidden meal, or a day of another week, closes the inspector.
  const standing = standingSelection(
    selection,
    shown.map((entry) => entry.id),
    fuelByDay.size > 0 ? days : [],
  );
  const selectedEntry = standing?.kind === "entry" ? (shown.find((entry) => entry.id === standing.id) ?? null) : null;
  const selectedDay = standing?.kind === "day" ? standing.date : null;

  const foodResults = searchCatalog(pickerQuery, ingredients);

  const choose = (next: Exclude<LedgerSelection, null>, from: HTMLElement) => {
    const after = toggleSelection(standing, next);
    setSharing(null);
    setLogged(null);
    if (after === null) {
      closeInspector();
      return;
    }
    opener.current = from;
    setSelection(after);
  };

  const chooseView = (next: PlanView) => {
    setView(next);
    try {
      window.localStorage.setItem(VIEW_KEY, next);
    } catch {
      // Not remembered, but the view still changes.
    }
  };

  const changeWeek = (next: string) => {
    setSelection(null);
    setWeekStart(next);
  };

  const openPicker = (date: string, mealType: (typeof MEAL_TYPES)[number]) => {
    // On a phone the picker and the inspector would share the bottom of the screen.
    setSelection(null);
    setSharing(null);
    setPicker({ date, mealType });
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
    closeInspector();
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
    setLogged({ entryId: entry.id, text: `${entryName(entry)} logged.` });
  };

  // A meal eaten alone logs in one tap; a shared one asks for your share first.
  const startLog = (entry: Entry) => {
    setLogged(null);
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

  const dinerLabels = (entry: Entry) =>
    members.flatMap((member, index) =>
      entry.diners.some((diner) => diner.id === member.id) ? [{ id: member.id, label: memberLabels[index], name: member.displayName }] : [],
    );

  const entryInspector = (entry: Entry) => {
    const what = plannedWhat(entry);
    return (
      <section className="inspector" aria-labelledby={INSPECTOR_TITLE}>
        <div className="inspector-head">
          <p className="eyebrow">
            {dayLabel(entry.date)} · {mealLabel(entry.mealType)}
          </p>
          <button className="button secondary small" onClick={closeInspector} aria-label="Close details">
            ✕
          </button>
        </div>
        <h2 id={INSPECTOR_TITLE} className="inspector-title" ref={inspectorTitle} tabIndex={-1}>
          {what.kind === "recipe" ? <Link href={`/app/recipes/${what.recipeId}`}>{what.title}</Link> : what.food.name}
        </h2>
        <p className="muted inspector-kind">{what.kind === "recipe" ? "Recipe" : "Food"}</p>

        <div className="inspector-section">
          <span className="label">{what.kind === "recipe" ? "Servings" : "Amount"}</span>
          {what.kind === "recipe" ? (
            <div className="stepper">
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
            </div>
          ) : (
            <FoodAmount
              name={what.food.name}
              amount={entry.servings}
              unit={what.food.unit}
              onSave={(amount) => changeAmount(entry.id, amount)}
            />
          )}
        </div>

        {shared && (
          <div className="inspector-section">
            <span className="label">Who eats it</span>
            <div className="diners" role="group" aria-label="Who eats it">
              {members.map((member) => {
                const eats = entry.diners.some((diner) => diner.id === member.id);
                const last = eats && entry.diners.length === 1;
                return (
                  <button
                    key={member.id}
                    className={eats ? "button small" : "button secondary small"}
                    aria-pressed={eats}
                    aria-label={member.you ? `${member.displayName} (you)` : member.displayName}
                    title={last ? `${member.displayName} is the only one eating it` : undefined}
                    disabled={last}
                    onClick={() => void changeDiners(entry, member.id)}
                  >
                    {member.displayName}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        <div className="inspector-section inspector-actions">
          {eatsIt(entry) &&
            (sharing?.entryId === entry.id ? (
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
            ) : (
              <button className="button" onClick={() => startLog(entry)}>
                Log
              </button>
            ))}
          {logged?.entryId === entry.id && (
            <p className="ok-text" role="status">
              {logged.text}
            </p>
          )}
          <button className="button danger" onClick={() => void unplan(entry.id)} aria-label={`Remove ${entryName(entry)}`}>
            Remove from plan
          </button>
        </div>
      </section>
    );
  };

  const dayInspector = (date: string) => {
    const day = fuelByDay.get(date);
    return (
      <section className="inspector" aria-labelledby={INSPECTOR_TITLE}>
        <div className="inspector-head">
          <p className="eyebrow">{date === today ? `Today · ${dayLabel(date)}` : dayLabel(date)}</p>
          <button className="button secondary small" onClick={closeInspector} aria-label="Close details">
            ✕
          </button>
        </div>
        <h2 id={INSPECTOR_TITLE} className="inspector-title" ref={inspectorTitle} tabIndex={-1}>
          {weekdayName(date)}
        </h2>
        {day?.target && <TrainingSlot date={date} sessions={day.sessions} onChange={() => reload()} />}
        <DayFuel date={date} target={day?.target} planned={day?.planned} />
      </section>
    );
  };

  const hint = () => {
    const mine = shown.filter(eatsIt).length;
    const sessions = [...fuelByDay.values()].reduce((count, day) => count + (day.target ? day.sessions.length : 0), 0);
    return (
      <section className="inspector inspector-hint" aria-label="This week">
        <p className="eyebrow">This week</p>
        <p className="inspector-title">Select a meal or a day</p>
        <p className="muted">Its servings, who eats it, Log, and Remove open here.</p>
        <dl className="inspector-stats">
          <div>
            <dt>{mine === 1 ? "meal you eat" : "meals you eat"}</dt>
            <dd>{mine}</dd>
          </div>
          {targetsOn && (
            <div>
              <dt>{sessions === 1 ? "training session" : "training sessions"}</dt>
              <dd>{sessions}</dd>
            </div>
          )}
        </dl>
      </section>
    );
  };

  return (
    <div className="plan-ledger">
      <PageHeader
        eyebrow={`Week of ${dayLabel(weekStart)}`}
        title="Plan"
        actions={
          <>
            <button className="button secondary" onClick={() => changeWeek(addDays(weekStart, -7))}>
              ← Previous
            </button>
            <button className="button secondary" onClick={() => changeWeek(mondayOf(new Date()))}>
              This week
            </button>
            <button className="button secondary" onClick={() => changeWeek(addDays(weekStart, 7))}>
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

      <div className="ledger-layout">
        {plan === null ? (
          <p className="muted">Loading…</p>
        ) : (
          <div className="ledger-scroll">
            <table className="ledger" aria-label={`Meals for the week of ${dayLabel(weekStart)}`}>
              <colgroup>
                <col className="ledger-rowhead" />
                {days.map((day) => (
                  <col key={day} />
                ))}
              </colgroup>
              <thead>
                <tr>
                  <td />
                  {days.map((day) => {
                    const head = dayHead(day);
                    const content = (
                      <>
                        <span className="ledger-weekday">{day === today ? "Today" : head.weekday}</span>
                        <span className="ledger-date">{head.date}</span>
                      </>
                    );
                    return (
                      <th key={day} scope="col" className={day === today ? "today" : undefined}>
                        {fuelByDay.size > 0 ? (
                          <button
                            className="ledger-day"
                            aria-label={day === today ? `${dayLabel(day)}, today` : dayLabel(day)}
                            aria-pressed={selectedDay === day}
                            onClick={(event) => choose({ kind: "day", date: day }, event.currentTarget)}
                          >
                            {content}
                          </button>
                        ) : (
                          <span className="ledger-day" aria-label={dayLabel(day)}>
                            {content}
                          </span>
                        )}
                      </th>
                    );
                  })}
                </tr>
              </thead>
              <tbody>
                {targetsOn && (
                  <tr className="ledger-meta-row">
                    <th scope="row">{TRAINING_LABEL}</th>
                    {days.map((day) => {
                      const sessions = fuelByDay.get(day)?.sessions ?? [];
                      return (
                        <td key={day}>
                          <button
                            className="ledger-meta"
                            aria-label={`${trainingLabelFor(day)}: ${sessions.length > 0 ? sessions.map(sessionShort).join(", ") : "none"}`}
                            onClick={(event) => choose({ kind: "day", date: day }, event.currentTarget)}
                          >
                            {sessions.length > 0 ? (
                              sessions.map((session) => <span key={session.id}>{sessionShort(session)}</span>)
                            ) : (
                              <span className="muted">Rest</span>
                            )}
                          </button>
                        </td>
                      );
                    })}
                  </tr>
                )}
                {fuelByDay.size > 0 && (
                  <tr className="ledger-meta-row">
                    <th scope="row">Planned</th>
                    {days.map((day) => {
                      const figures = ledgerFuel(fuelByDay.get(day)?.target, fuelByDay.get(day)?.planned);
                      return (
                        <td key={day}>
                          <button
                            className="ledger-meta"
                            aria-label={ledgerFuelLabel(day, figures)}
                            onClick={(event) => choose({ kind: "day", date: day }, event.currentTarget)}
                          >
                            <span className="ledger-figure">{figures.value}</span>
                            {figures.detail && <span className="muted">{figures.detail}</span>}
                          </button>
                        </td>
                      );
                    })}
                  </tr>
                )}
                {MEAL_TYPES.map((mealType) => (
                  <tr key={mealType}>
                    <th scope="row">{mealLabel(mealType)}</th>
                    {days.map((day) => {
                      const entries = entriesBySlot.get(`${day}:${mealType}`) ?? [];
                      return (
                        <td key={day} className={day === today ? "today" : undefined}>
                          <div className={entries.length === 0 ? "ledger-cell empty" : "ledger-cell"}>
                            {entries.map((entry) => (
                              <button
                                key={entry.id}
                                className={eatsIt(entry) ? "ledger-entry" : "ledger-entry others"}
                                aria-pressed={selectedEntry?.id === entry.id}
                                onClick={(event) => choose({ kind: "entry", id: entry.id }, event.currentTarget)}
                              >
                                <span className="ledger-entry-name">{entryName(entry)}</span>
                                <span className="ledger-entry-meta">
                                  {amountText(entry)}
                                  {shared && (
                                    <span className="ledger-diners">
                                      {dinerLabels(entry).map((diner) => (
                                        <span key={diner.id} className="ledger-diner" title={diner.name}>
                                          {diner.label}
                                        </span>
                                      ))}
                                    </span>
                                  )}
                                </span>
                              </button>
                            ))}
                            <button
                              className="ledger-add"
                              onClick={() => openPicker(day, mealType)}
                              aria-label={`Add ${mealLabel(mealType)} for ${dayLabel(day)}`}
                            >
                              +
                            </button>
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <aside className="ledger-rail" aria-label="Details">
          {selectedEntry ? entryInspector(selectedEntry) : selectedDay ? dayInspector(selectedDay) : hint()}

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
            <section className="suggestions" aria-label="Suggestions from plugins">
              <h2>Suggestions</h2>
              <p className="muted">
                From the <Link href="/app/plugins">plugins</Link> you turned on.
              </p>
              {notice && (
                <p className="ok-text" role="status">
                  {notice}
                </p>
              )}
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
        </aside>
      </div>

      {picker && (
        <div className="picker card" role="dialog" aria-label="Pick a meal">
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
    </div>
  );
}
