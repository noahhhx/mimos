"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import {
  addShoppingListItem,
  generateShoppingList,
  getShoppingList,
  removeShoppingListItem,
  updateShoppingListItem,
  type ShoppingList,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { apiClient } from "@/lib/api";
import { Quantity } from "@/components/quantity";
import { addDays, countedName, dayLabel, mondayOf } from "@/lib/format";
import { ADD_ITEM_UNITS, addedItem, aislesToShop, type AddItemFields } from "@/lib/shopping";

const NO_FIELDS: AddItemFields = { name: "", quantity: "", unit: "" };

/**
 * The week's shopping list, grouped by aisle and checkable with one thumb
 * in the store, with what's still to buy on top. Regenerating keeps what
 * you've already ticked off, and anything you added by hand (ADR-0027).
 */
export default function ShoppingListPage() {
  const { user } = useAuth();
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [list, setList] = useState<ShoppingList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [neverGenerated, setNeverGenerated] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [fields, setFields] = useState<AddItemFields>(NO_FIELDS);
  const [adding, setAdding] = useState(false);

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
        <PageHeader title="Shopping list" />
        <SignInPrompt>You need to sign in to use Mimos.</SignInPrompt>
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
      setError("Could not update the item. Refreshing.");
      await reload();
    }
  };

  const add = async (event: FormEvent) => {
    event.preventDefault();
    const parsed = addedItem(fields);
    if ("error" in parsed) {
      setError(parsed.error);
      return;
    }
    setAdding(true);
    const result = await addShoppingListItem({
      client: apiClient,
      path: { startDate: weekStart },
      body: parsed.input,
    });
    setAdding(false);
    if (result.error) {
      setError("Could not add the item.");
      return;
    }
    setError(null);
    setFields(NO_FIELDS);
    // A week with no list gets one from its plan first, so load the whole list again.
    await reload();
  };

  const remove = async (itemId: string) => {
    setList((current) =>
      current ? { ...current, items: current.items.filter((item) => item.id !== itemId) } : current,
    );
    const result = await removeShoppingListItem({ client: apiClient, path: { startDate: weekStart, itemId } });
    if (result.error) {
      setError("Could not remove the item. Refreshing.");
      await reload();
    }
  };

  const aisles = aislesToShop(list?.items ?? []);

  return (
    <>
      <PageHeader
        eyebrow={`Week of ${dayLabel(weekStart)}`}
        title="Shopping list"
        actions={
          <>
            <button className="button secondary" onClick={() => setWeekStart(addDays(weekStart, -7))}>
              ← Previous
            </button>
            <button className="button secondary" onClick={() => setWeekStart(addDays(weekStart, 7))}>
              Next →
            </button>
            <Link className="button secondary" href={`/app/plan?week=${weekStart}`}>
              Plan
            </Link>
          </>
        }
      />

      <p className="generate">
        <button className="button" onClick={() => void generate()} disabled={generating}>
          {generating ? "Generating…" : neverGenerated ? "Generate from this week's plan" : "Regenerate"}
        </button>
      </p>
      <p className="muted">
        {neverGenerated
          ? "No list for this week yet. Generate one from the plan."
          : list
            ? `Generated ${new Date(list.generatedAt).toLocaleString()} · regenerating keeps checked-off and added items.`
            : ""}
      </p>

      {error && (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      )}

      <form className="add-item" onSubmit={(event) => void add(event)}>
        <h2>Add an item</h2>
        <div className="field-row">
          <label className="add-item-name">
            Item
            <input
              type="text"
              maxLength={200}
              placeholder="Anything else to buy"
              value={fields.name}
              onChange={(e) => setFields({ ...fields, name: e.target.value })}
            />
          </label>
          <label className="add-item-amount">
            Amount
            <input
              type="number"
              min={0}
              step="any"
              value={fields.quantity}
              onChange={(e) => setFields({ ...fields, quantity: e.target.value })}
            />
          </label>
          <label className="add-item-unit">
            Unit
            <select value={fields.unit} onChange={(e) => setFields({ ...fields, unit: e.target.value })}>
              {ADD_ITEM_UNITS.map((unit) => (
                <option key={unit} value={unit}>
                  {unit || "pieces"}
                </option>
              ))}
            </select>
          </label>
          <button className="button" type="submit" disabled={adding}>
            {adding ? "Adding…" : "Add"}
          </button>
        </div>
      </form>

      {list && list.items.length === 0 && (
        <div className="card">
          <p>Nothing to buy. Plan some meals, or add an item.</p>
        </div>
      )}

      {aisles.map(({ category, items }) => (
        <section key={category} className="aisle">
          <h2>{category}</h2>
          <ul className="shopping-items">
            {items.map((item) => (
              <li key={item.id}>
                <label className={item.checked ? "done" : undefined}>
                  <input
                    type="checkbox"
                    checked={item.checked}
                    onChange={(e) => void toggle(item.id, e.target.checked)}
                  />{" "}
                  <Quantity quantity={item.quantity} unit={item.unit} />{" "}
                  {countedName(item.name, item.quantity, item.unit)}
                </label>
                {item.added && (
                  <>
                    {" "}
                    <span className="muted">Added</span>{" "}
                    <button
                      className="button secondary small"
                      aria-label={`Remove ${item.name}`}
                      onClick={() => void remove(item.id)}
                    >
                      Remove
                    </button>
                  </>
                )}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </>
  );
}
