"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

import { addMealPlanEntry } from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { DayInspector, EntryInspector, WeekHint } from "@/components/plan/inspector";
import { Ledger } from "@/components/plan/ledger";
import { MealPicker, type PlanItem, type PlanSlot } from "@/components/plan/meal-picker";
import { Suggestions } from "@/components/plan/suggestions";
import { usePlanWeek } from "@/components/plan/use-plan-week";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { WeekPanelSection } from "@/components/week-panel";
import { apiClient } from "@/lib/api";
import { eatsIt, parsePlanView, visibleEntries, type PlanView } from "@/lib/diners";
import { addDays, dayLabel, mondayOf, todayIso, weekDays } from "@/lib/format";
import { standingSelection, toggleSelection, type LedgerSelection } from "@/lib/plan-ledger";
import { showsPlannedTotals } from "@/lib/planned-totals";

const VIEW_KEY = "mimos.plan.view";

/**
 * The week planner, laid out as a ledger: days across, meals down, and
 * cells that show only what is planned. Selecting a planned meal or a day
 * opens its controls in the inspector beside the table (a sheet on narrow
 * screens): servings or amount, who eats it, Log, and Remove for a meal;
 * training and fuel for a day. Plugins' panels and suggestions sit in the
 * same rail, under the inspector. In a shared household (ADR-0019) each
 * meal shows who eats it, the week shows your meals or everyone's, and
 * logging a shared meal logs your share.
 *
 * This page owns what the person is looking at (the week, Mine or
 * Everyone, the selection, the open picker); `usePlanWeek` owns what was
 * read for that week.
 */
export default function PlanPage() {
  const { user } = useAuth();
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [view, setView] = useState<PlanView>("mine");
  const [selection, setSelection] = useState<LedgerSelection>(null);
  const [picker, setPicker] = useState<PlanSlot | null>(null);
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

  const {
    plan,
    fuel,
    suggestions,
    panels,
    members,
    ingredients,
    addIngredient,
    error,
    setError,
    reload,
    replacePanel,
  } = usePlanWeek(weekStart, user?.profile.sub);

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
  const shown = shared ? visibleEntries(plan?.entries ?? [], view) : (plan?.entries ?? []);
  const days = weekDays(weekStart);
  const today = todayIso();
  const fuelByDay = new Map(
    fuel !== null && fuel.week === weekStart && showsPlannedTotals(shared, view)
      ? fuel.days.map((day) => [day.date, day])
      : [],
  );
  const targetsOn = [...fuelByDay.values()].some((day) => day.target !== undefined);

  // What is selected, if it is still there: a removed or hidden meal, or a day of another week, closes the inspector.
  const standing = standingSelection(
    selection,
    shown.map((entry) => entry.id),
    fuelByDay.size > 0 ? days : [],
  );
  const selectedEntry = standing?.kind === "entry" ? (shown.find((entry) => entry.id === standing.id) ?? null) : null;
  const selectedDay = standing?.kind === "day" ? standing.date : null;

  const refresh = () => reload();

  const choose = (next: Exclude<LedgerSelection, null>, from: HTMLElement) => {
    const after = toggleSelection(standing, next);
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

  const openPicker = (date: string, mealType: PlanSlot["mealType"]) => {
    // On a phone the picker and the inspector would share the bottom of the screen.
    setSelection(null);
    setPicker({ date, mealType });
  };

  const planItem = async (item: PlanItem) => {
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
    setPicker(null);
    await reload();
  };

  return (
    <div className="plan-ledger">
      <PageHeader
        eyebrow={`Week of ${dayLabel(weekStart)}`}
        title="Plan"
        actions={
          <>
            <button type="button" className="button secondary" onClick={() => changeWeek(addDays(weekStart, -7))}>
              ← Previous
            </button>
            <button type="button" className="button secondary" onClick={() => changeWeek(mondayOf(new Date()))}>
              This week
            </button>
            <button type="button" className="button secondary" onClick={() => changeWeek(addDays(weekStart, 7))}>
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
            type="button"
            role="tab"
            aria-selected={view === "mine"}
            className={view === "mine" ? "active" : undefined}
            onClick={() => chooseView("mine")}
          >
            Mine
          </button>
          <button
            type="button"
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
          <Ledger
            weekStart={weekStart}
            days={days}
            today={today}
            entries={shown}
            members={members}
            shared={shared}
            fuelByDay={fuelByDay}
            targetsOn={targetsOn}
            selectedEntryId={selectedEntry?.id}
            selectedDay={selectedDay}
            onChoose={choose}
            onAdd={openPicker}
          />
        )}

        <aside className="ledger-rail" aria-label="Details">
          {selectedEntry ? (
            <EntryInspector
              key={selectedEntry.id}
              entry={selectedEntry}
              weekStart={weekStart}
              members={members}
              ingredients={ingredients}
              titleRef={inspectorTitle}
              onChanged={refresh}
              onError={setError}
              onClose={closeInspector}
            />
          ) : selectedDay ? (
            <DayInspector
              date={selectedDay}
              today={today}
              day={fuelByDay.get(selectedDay)}
              titleRef={inspectorTitle}
              onChanged={refresh}
              onClose={closeInspector}
            />
          ) : (
            <WeekHint
              mine={shown.filter(eatsIt).length}
              sessions={[...fuelByDay.values()].reduce(
                (count, day) => count + (day.target ? day.sessions.length : 0),
                0,
              )}
              targetsOn={targetsOn}
            />
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

          <Suggestions weekStart={weekStart} suggestions={suggestions} onApplied={refresh} />
        </aside>
      </div>

      {picker && (
        <MealPicker
          slot={picker}
          householdSize={members.length}
          ingredients={ingredients}
          onIngredientAdded={addIngredient}
          onPlan={planItem}
          onError={setError}
          onClose={() => setPicker(null)}
        />
      )}
    </div>
  );
}
