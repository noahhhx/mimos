"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import {
  generateShoppingList,
  getShoppingList,
  updateShoppingListItem,
  type ShoppingList,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { apiClient } from "@/lib/api";
import { Quantity } from "@/components/quantity";
import { addDays, dayLabel, mondayOf } from "@/lib/format";

const CATEGORY_ORDER = [
  "Produce",
  "Meat & Seafood",
  "Dairy & Eggs",
  "Bakery",
  "Frozen",
  "Pantry",
  "Other",
];

/**
 * The week's shopping list, grouped by aisle and checkable with one thumb
 * in the store. Regenerating keeps what you've already ticked off.
 */
export default function ShoppingListPage() {
  const { user, signIn } = useAuth();
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [list, setList] = useState<ShoppingList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [neverGenerated, setNeverGenerated] = useState(false);
  const [generating, setGenerating] = useState(false);

  useEffect(() => {
    // Honor ?week= from the plan page's cross-link. Read from the URL in an
    // effect (not useSearchParams) so the page stays statically prerendered.
    const week = new URLSearchParams(window.location.search).get("week");
    if (week && /^\d{4}-\d{2}-\d{2}$/.test(week)) {
      setWeekStart(mondayOf(new Date(`${week}T00:00:00`)));
    }
  }, []);

  const reload = useCallback(async () => {
    setNeverGenerated(false);
    const result = await getShoppingList({ client: apiClient, path: { startDate: weekStart } });
    if (result.error) {
      if (result.response?.status === 404) {
        setNeverGenerated(true);
        setList(null);
        return;
      }
      setError("Could not load the shopping list.");
      return;
    }
    setError(null);
    setList(result.data ?? null);
  }, [weekStart]);

  useEffect(() => {
    if (user) {
      void reload();
    }
  }, [user, reload]);

  if (!user) {
    return (
      <>
        <h1>Shopping list</h1>
        <p>You need to sign in to use Mimos.</p>
        <button className="button" onClick={() => void signIn()}>
          Sign in
        </button>
      </>
    );
  }

  const generate = async () => {
    setGenerating(true);
    const result = await generateShoppingList({ client: apiClient, path: { startDate: weekStart } });
    setGenerating(false);
    if (result.error) {
      setError("Could not generate the shopping list.");
      return;
    }
    setError(null);
    setList(result.data ?? null);
  };

  const toggle = async (itemId: string, checked: boolean) => {
    // Optimistic: the store is not the place for spinners.
    setList((current) =>
      current
        ? {
            ...current,
            items: current.items.map((item) => (item.id === itemId ? { ...item, checked } : item)),
          }
        : current,
    );
    const result = await updateShoppingListItem({
      client: apiClient,
      path: { startDate: weekStart, itemId },
      body: { checked },
    });
    if (result.error) {
      setError("Could not update the item — refreshing.");
      await reload();
    }
  };

  const byCategory = new Map<string, ShoppingList["items"]>();
  for (const item of list?.items ?? []) {
    byCategory.set(item.category, [...(byCategory.get(item.category) ?? []), item]);
  }
  const categories = [...byCategory.keys()].sort(
    (a, b) => CATEGORY_ORDER.indexOf(a) - CATEGORY_ORDER.indexOf(b),
  );

  return (
    <>
      <div className="toolbar">
        <h1>Shopping list</h1>
        <div className="actions">
          <Link className="button secondary" href={`/app/plan?week=${weekStart}`}>
            Plan
          </Link>
        </div>
      </div>

      <div className="toolbar week-nav">
        <button className="button secondary" onClick={() => setWeekStart(addDays(weekStart, -7))}>
          ← Previous
        </button>
        <strong>Week of {dayLabel(weekStart)}</strong>
        <button className="button secondary" onClick={() => setWeekStart(addDays(weekStart, 7))}>
          Next →
        </button>
      </div>

      <p>
        <button className="button" onClick={() => void generate()} disabled={generating}>
          {generating ? "Generating…" : neverGenerated ? "Generate from this week's plan" : "Regenerate"}
        </button>
      </p>
      <p className="muted">
        {neverGenerated
          ? "No list for this week yet — generate one from the plan."
          : list
            ? `Generated ${new Date(list.generatedAt).toLocaleString()} · regenerating keeps checked-off items.`
            : ""}
      </p>

      {error && (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      )}

      {list && list.items.length === 0 && (
        <div className="card">
          <p>Nothing to buy — plan some meals first.</p>
        </div>
      )}

      {categories.map((category) => (
        <section key={category} className="card">
          <h2>{category}</h2>
          <ul className="shopping-items">
            {(byCategory.get(category) ?? []).map((item) => (
              <li key={item.id}>
                <label className={item.checked ? "done" : undefined}>
                  <input
                    type="checkbox"
                    checked={item.checked}
                    onChange={(e) => void toggle(item.id, e.target.checked)}
                  />{" "}
                  <Quantity quantity={item.quantity} unit={item.unit} /> {item.name}
                </label>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </>
  );
}
