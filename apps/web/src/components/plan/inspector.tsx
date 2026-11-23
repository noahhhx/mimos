"use client";

import Link from "next/link";
import { useState, type Ref } from "react";

import {
  createMealLog,
  deleteMealPlanEntry,
  updateMealPlanEntry,
  type CatalogIngredient,
  type FuelDay,
  type MealPlan,
  type Person,
} from "@mimos/api-client";

import { DayFuel } from "@/components/day-fuel";
import { FoodAmount } from "@/components/food-amount";
import { TrainingSlot } from "@/components/training-slot";
import { apiClient } from "@/lib/api";
import { eatsIt, toggleDiner } from "@/lib/diners";
import { dayLabel, formatServings, mealLabel, weekdayName } from "@/lib/format";
import { amountText, entryName, foodLogBody, plannedWhat } from "@/lib/planned-item";

type Entry = MealPlan["entries"][number];

const INSPECTOR_TITLE = "plan-inspector-title";

/**
 * A selected meal's controls: servings or amount, who eats it, Log, and
 * Remove. Mounted once per meal (keyed by its id), so a half-typed share or
 * a "logged" note never carries over to another meal. In a shared household
 * (ADR-0019) logging a shared meal asks for your share first.
 */
export function EntryInspector({
  entry,
  weekStart,
  members,
  ingredients,
  titleRef,
  onChanged,
  onError,
  onClose,
}: {
  entry: Entry;
  weekStart: string;
  members: Person[];
  ingredients: CatalogIngredient[];
  titleRef: Ref<HTMLHeadingElement>;
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
  onClose: () => void;
}) {
  // Your share of a shared meal, as typed, while its Log form is open.
  const [share, setShare] = useState<string | null>(null);
  const [logged, setLogged] = useState<string | null>(null);
  const shared = members.length > 1;
  const what = plannedWhat(entry);

  const changeServings = async (servings: number) => {
    const next = Math.round(servings * 2) / 2;
    if (next < 0.5 || next > 100) {
      return;
    }
    await updateMealPlanEntry({
      client: apiClient,
      path: { startDate: weekStart, entryId: entry.id },
      body: { servings: next },
    });
    await onChanged();
  };

  const changeAmount = async (amount: number) => {
    const result = await updateMealPlanEntry({
      client: apiClient,
      path: { startDate: weekStart, entryId: entry.id },
      body: { servings: amount },
    });
    if (result.error) {
      onError("Could not change that amount.");
    }
    await onChanged();
  };

  const changeDiners = async (memberId: string) => {
    const diners = toggleDiner(
      entry.diners.map((diner) => diner.id),
      memberId,
    );
    if (diners === null) {
      return;
    }
    const result = await updateMealPlanEntry({
      client: apiClient,
      path: { startDate: weekStart, entryId: entry.id },
      body: { diners },
    });
    if (result.error) {
      onError("Could not change who eats that meal.");
      return;
    }
    await onChanged();
  };

  const unplan = async () => {
    await deleteMealPlanEntry({ client: apiClient, path: { startDate: weekStart, entryId: entry.id } });
    onClose();
    await onChanged();
  };

  // A recipe logs as servings of it; a food logs as an ad-hoc meal of its amount, with the nutrition calculated here.
  const logMeal = async (amount: number) => {
    const body =
      what.kind === "recipe"
        ? { date: entry.date, mealType: entry.mealType, recipeId: what.recipeId, servings: amount }
        : foodLogBody(entry, ingredients, amount);
    const result = body && (await createMealLog({ client: apiClient, body }));
    if (!result || result.error) {
      onError("Could not log that meal.");
      return;
    }
    setShare(null);
    setLogged(`${entryName(entry)} logged.`);
  };

  // A meal eaten alone logs in one tap; a shared one asks for your share first.
  const startLog = () => {
    setLogged(null);
    if (entry.diners.length > 1) {
      setShare(String(entry.yourShare ?? entry.servings));
    } else {
      void logMeal(entry.servings);
    }
  };

  return (
    <section className="inspector" aria-labelledby={INSPECTOR_TITLE}>
      <div className="inspector-head">
        <p className="eyebrow">
          {dayLabel(entry.date)} · {mealLabel(entry.mealType)}
        </p>
        <button type="button" className="button secondary small" onClick={onClose} aria-label="Close details">
          ✕
        </button>
      </div>
      <h2 id={INSPECTOR_TITLE} className="inspector-title" ref={titleRef} tabIndex={-1}>
        {what.kind === "recipe" ? <Link href={`/app/recipes/${what.recipeId}`}>{what.title}</Link> : what.food.name}
      </h2>
      <p className="muted inspector-kind">{what.kind === "recipe" ? "Recipe" : "Food"}</p>

      <div className="inspector-section">
        <span className="label">{what.kind === "recipe" ? "Servings" : "Amount"}</span>
        {what.kind === "recipe" ? (
          <div className="stepper">
            <button
              type="button"
              className="button secondary small"
              onClick={() => void changeServings(entry.servings - 0.5)}
              aria-label="Fewer servings"
            >
              −
            </button>
            <span>{formatServings(entry.servings)}</span>
            <button
              type="button"
              className="button secondary small"
              onClick={() => void changeServings(entry.servings + 0.5)}
              aria-label="More servings"
            >
              +
            </button>
          </div>
        ) : (
          <FoodAmount name={what.food.name} amount={entry.servings} unit={what.food.unit} onSave={changeAmount} />
        )}
      </div>

      {shared && (
        <div className="inspector-section">
          <span className="label">Who eats it</span>
          {/* biome-ignore lint/a11y/useSemanticElements: a <fieldset> has the same group role but brings its own border, padding, and form semantics. */}
          <div className="diners" role="group" aria-label="Who eats it">
            {members.map((member) => {
              const eats = entry.diners.some((diner) => diner.id === member.id);
              const last = eats && entry.diners.length === 1;
              return (
                <button
                  type="button"
                  key={member.id}
                  className={eats ? "button small" : "button secondary small"}
                  aria-pressed={eats}
                  aria-label={member.you ? `${member.displayName} (you)` : member.displayName}
                  title={last ? `${member.displayName} is the only one eating it` : undefined}
                  disabled={last}
                  onClick={() => void changeDiners(member.id)}
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
          (share !== null ? (
            <form
              className="share"
              aria-label={`Log your share of ${entryName(entry)}`}
              onSubmit={(event) => {
                event.preventDefault();
                void logMeal(Number(share));
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
                  value={share}
                  onChange={(event) => setShare(event.target.value)}
                />
              </label>
              <span className="muted">
                of {amountText(entry)} for {entry.diners.length} people
              </span>
              <button className="button small" type="submit">
                Log share
              </button>
              <button className="button secondary small" type="button" onClick={() => setShare(null)}>
                Cancel
              </button>
            </form>
          ) : (
            <button type="button" className="button" onClick={startLog}>
              Log
            </button>
          ))}
        {logged !== null && (
          <p className="ok-text" role="status">
            {logged}
          </p>
        )}
        <button
          type="button"
          className="button danger"
          onClick={() => void unplan()}
          aria-label={`Remove ${entryName(entry)}`}
        >
          Remove from plan
        </button>
      </div>
    </section>
  );
}

/** A selected day: its training, while targets are on, and its fuel. */
export function DayInspector({
  date,
  today,
  day,
  titleRef,
  onChanged,
  onClose,
}: {
  date: string;
  today: string;
  day: FuelDay | undefined;
  titleRef: Ref<HTMLHeadingElement>;
  onChanged: () => Promise<void>;
  onClose: () => void;
}) {
  return (
    <section className="inspector" aria-labelledby={INSPECTOR_TITLE}>
      <div className="inspector-head">
        <p className="eyebrow">{date === today ? `Today · ${dayLabel(date)}` : dayLabel(date)}</p>
        <button type="button" className="button secondary small" onClick={onClose} aria-label="Close details">
          ✕
        </button>
      </div>
      <h2 id={INSPECTOR_TITLE} className="inspector-title" ref={titleRef} tabIndex={-1}>
        {weekdayName(date)}
      </h2>
      {day?.target && <TrainingSlot date={date} sessions={day.sessions} onChange={onChanged} />}
      <DayFuel date={date} target={day?.target} planned={day?.planned} />
    </section>
  );
}

/** What the inspector shows with nothing selected: how to use it, and the week in two numbers. */
export function WeekHint({ mine, sessions, targetsOn }: { mine: number; sessions: number; targetsOn: boolean }) {
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
}
