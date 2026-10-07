"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import {
  getMealPlan,
  getPlanSuggestions,
  getRecipe,
  getShoppingList,
  type MealPlan,
  type PlanSuggestion,
  type RecipeDetail,
  type ShoppingList,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { Quantity } from "@/components/quantity";
import { SplitPage } from "@/components/split-page";
import { apiClient } from "@/lib/api";
import { mondayOf, shortDayLabel, todayIso, weekdayName } from "@/lib/format";
import { greeting, pickThought, pickTonight, stillToBuy, totalMinutes, weekDinners } from "@/lib/kitchen";
import { applySuggestionEntries } from "@/lib/suggestions";

/** One block's data: each block on the page loads and fails on its own. */
type Load<T> = { state: "loading" } | { state: "error" } | { state: "ok"; data: T };

const LOADING = { state: "loading" } as const;
const FAILED = { state: "error" } as const;

/**
 * The Kitchen home (docs/design/index.md, "Kitchen home"): what's for dinner
 * tonight, the week's dinners in the rail, what's left to buy, and a
 * plugin's thought for an open evening. GET-only — loading it changes
 * nothing.
 */
export default function KitchenPage() {
  const { user } = useAuth();
  // Read once per visit: the greeting, today and the week all follow from it.
  const [now] = useState(() => new Date());
  const today = todayIso(now);
  const weekStart = mondayOf(now);

  const [plan, setPlan] = useState<Load<MealPlan>>(LOADING);
  const [recipe, setRecipe] = useState<Load<RecipeDetail>>(LOADING);
  // `null` data: the week's list has not been generated yet.
  const [shopping, setShopping] = useState<Load<ShoppingList | null>>(LOADING);
  const [suggestions, setSuggestions] = useState<PlanSuggestion[]>([]);
  const [applying, setApplying] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const loadPlan = useCallback(async () => {
    const [planResult, suggestionsResult] = await Promise.all([
      getMealPlan({ client: apiClient, path: { startDate: weekStart } }),
      getPlanSuggestions({ client: apiClient, path: { startDate: weekStart } }),
    ]);
    setPlan(planResult.data ? { state: "ok", data: planResult.data } : FAILED);
    // Suggestions are an enhancement (ADR-0006): none, or a failure, hides the block.
    setSuggestions(suggestionsResult.data?.suggestions ?? []);
  }, [weekStart]);

  useEffect(() => {
    if (!user) {
      return;
    }
    let cancelled = false;
    void loadPlan();
    void getShoppingList({ client: apiClient, path: { startDate: weekStart } }).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.data) {
        setShopping({ state: "ok", data: result.data });
      } else {
        setShopping(result.response?.status === 404 ? { state: "ok", data: null } : FAILED);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [user, weekStart, loadPlan]);

  const tonight = plan.state === "ok" ? pickTonight(plan.data.entries, today) : null;
  const tonightRecipeId = tonight?.entry.recipeId ?? null;

  useEffect(() => {
    if (!tonightRecipeId) {
      return;
    }
    let cancelled = false;
    setRecipe(LOADING);
    void getRecipe({ client: apiClient, path: { recipeId: tonightRecipeId } }).then((result) => {
      if (!cancelled) {
        setRecipe(result.data ? { state: "ok", data: result.data } : FAILED);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [tonightRecipeId]);

  if (!user) {
    return (
      <>
        <PageHeader title="Mimos" />
        <SignInPrompt>You need to sign in to use Mimos.</SignInPrompt>
      </>
    );
  }

  const entries = plan.state === "ok" ? plan.data.entries : [];
  const thought = plan.state === "ok" ? pickThought(suggestions, entries, today) : null;
  const planLink = `/app/plan?week=${weekStart}`;

  const addThought = async () => {
    if (!thought) {
      return;
    }
    setApplying(true);
    const added = await applySuggestionEntries(weekStart, [thought.entry]);
    setApplying(false);
    setNotice(
      added > 0
        ? `Added ${thought.entry.recipeTitle} to ${weekdayName(thought.entry.date)}.`
        : "Could not add that suggestion.",
    );
    await loadPlan();
  };

  return (
    <SplitPage
      className="kitchen"
      rail={
        <>
          <header className="page-header">
            <p className="muted">
              {now.toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" })}
            </p>
            <h1>{greeting(now)}</h1>
          </header>
          <section aria-labelledby="week-heading">
            <h2 id="week-heading">This week</h2>
            {plan.state === "error" ? (
              <p className="card error" role="alert">
                Could not load this week&apos;s plan.
              </p>
            ) : plan.state === "loading" ? (
              <p className="muted">Loading…</p>
            ) : (
              <ul className="week-dinners">
                {weekDinners(entries, weekStart).map(({ date, dinner }) => (
                  <li key={date} className={date === today ? "today" : undefined}>
                    <span className="day">
                      {shortDayLabel(date)}
                      {date === today && <span className="visually-hidden"> (today)</span>}
                    </span>
                    {dinner ? (
                      <Link href={`/app/recipes/${dinner.recipeId}`}>{dinner.recipeTitle}</Link>
                    ) : (
                      <span className="open">Nothing yet</span>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      }
    >
      <section className="tonight" aria-labelledby="tonight-heading">
        <h2 id="tonight-heading">{tonight?.label ?? "Tonight"}</h2>
        {plan.state === "error" ? (
          <p className="card error" role="alert">
            Could not load tonight&apos;s meal.
          </p>
        ) : plan.state === "loading" ? (
          <p className="muted">Loading…</p>
        ) : tonight === null ? (
          <>
            <h3>Nothing planned for tonight</h3>
            <div className="buttons">
              <Link className="button" href={planLink}>
                Plan tonight
              </Link>
            </div>
          </>
        ) : (
          <>
            <h3>{tonight.entry.recipeTitle}</h3>
            {recipe.state === "error" && (
              <p className="card error" role="alert">
                Could not load the recipe&apos;s details.
              </p>
            )}
            {recipe.state === "ok" && recipe.data.id === tonight.entry.recipeId && (
              <>
                <p className="description">{recipe.data.description}</p>
                <TonightFacts recipe={recipe.data} servings={tonight.entry.servings} />
              </>
            )}
            <div className="buttons">
              <Link className="button" href={`/app/recipes/${tonight.entry.recipeId}`}>
                Start cooking
              </Link>
              <Link className="button secondary" href={planLink}>
                Swap meal
              </Link>
            </div>
          </>
        )}
      </section>

      <div className="kitchen-pair">
        <section aria-labelledby="shopping-heading">
          <ShoppingBlock shopping={shopping} weekStart={weekStart} />
        </section>
        {(thought || notice) && (
          <section className="thought" aria-labelledby="thought-heading">
            {thought ? (
              <>
                <h2 id="thought-heading">A thought for {weekdayName(thought.entry.date)}</h2>
                <h3>{thought.suggestion.title}</h3>
                {thought.suggestion.blurb && <p className="muted">{thought.suggestion.blurb}</p>}
                <p>{thought.entry.recipeTitle} would fill the open evening.</p>
                <p className="from">
                  {thought.suggestion.icon && (
                    <span className="suggestion-icon" aria-hidden="true">
                      {thought.suggestion.icon}
                    </span>
                  )}
                  from {thought.suggestion.pluginName}
                </p>
                <button className="button secondary" disabled={applying} onClick={() => void addThought()}>
                  {applying ? "Adding…" : `Add to ${weekdayName(thought.entry.date)}`}
                </button>
              </>
            ) : (
              <h2 id="thought-heading">Suggestions</h2>
            )}
            {notice && (
              <p className="card ok" role="status">
                {notice}
              </p>
            )}
          </section>
        )}
      </div>
    </SplitPage>
  );
}

/** Tonight's minutes, servings and kcal per serving — each shown only when known. */
function TonightFacts({ recipe, servings }: { recipe: RecipeDetail; servings: number }) {
  const minutes = totalMinutes(recipe);
  const kcal = recipe.nutrition.calories;
  return (
    <dl className="facts">
      {minutes !== null && (
        <div>
          <dt>minutes</dt>
          <dd>{minutes}</dd>
        </div>
      )}
      <div>
        <dt>{servings === 1 ? "serving" : "servings"}</dt>
        <dd>{servings}</dd>
      </div>
      {kcal != null && (
        <div>
          <dt>kcal each</dt>
          <dd>{Math.round(kcal)}</dd>
        </div>
      )}
    </dl>
  );
}

/** "Still to buy": a few of the week's unchecked items, or why there are none. */
function ShoppingBlock({ shopping, weekStart }: { shopping: Load<ShoppingList | null>; weekStart: string }) {
  const listLink = `/app/shopping-list?week=${weekStart}`;
  if (shopping.state !== "ok" || shopping.data === null) {
    return (
      <>
        <h2 id="shopping-heading">Still to buy</h2>
        {shopping.state === "error" ? (
          <p className="card error" role="alert">
            Could not load the shopping list.
          </p>
        ) : shopping.state === "loading" ? (
          <p className="muted">Loading…</p>
        ) : (
          <p>
            No list yet. <Link href={listLink}>Make this week&apos;s list</Link>
          </p>
        )}
      </>
    );
  }
  const { shown, remaining, total } = stillToBuy(shopping.data.items);
  return (
    <>
      <h2 id="shopping-heading">{total > 0 ? `Still to buy · ${remaining} of ${total}` : "Still to buy"}</h2>
      {total === 0 ? (
        <p className="muted">Nothing to buy this week.</p>
      ) : (
        <ul className="to-buy">
          {shown.map((item) => (
            <li key={item.id} className={item.checked ? "got" : undefined}>
              <span className="box" aria-hidden="true" />
              <span>
                {item.checked && <span className="visually-hidden">Got: </span>}
                <Quantity quantity={item.quantity} unit={item.unit} /> {item.name}
              </span>
            </li>
          ))}
        </ul>
      )}
      <p className="more">
        <Link href={listLink}>Full list →</Link>
      </p>
    </>
  );
}
