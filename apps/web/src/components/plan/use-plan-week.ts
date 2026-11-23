"use client";

import { useCallback, useEffect, useState } from "react";

import {
  getHousehold,
  getMealPlan,
  getMyFuel,
  getPlanSuggestions,
  getWeekPanels,
  listIngredients,
  type CatalogIngredient,
  type FuelDay,
  type MealPlan,
  type Person,
  type PlanSuggestion,
  type WeekPanel,
} from "@mimos/api-client";

import { apiClient } from "@/lib/api";
import { addDays } from "@/lib/format";

/**
 * Everything the plan page reads for a week: the plan, the caller's fuel,
 * plugins' suggestions and panels, the household, and the ingredient
 * catalog. Only the plan failing is an error; the rest are enhancements
 * that fall back to empty. `reload` reads the week again after a change;
 * panels load only for a new week or signed-in subject.
 */
export function usePlanWeek(weekStart: string, subject: string | undefined) {
  const [plan, setPlan] = useState<MealPlan | null>(null);
  // The caller's own fuel for each day: what they plan to eat, and their target while targets are on.
  const [fuel, setFuel] = useState<{ week: string; days: FuelDay[] } | null>(null);
  const [suggestions, setSuggestions] = useState<PlanSuggestion[] | null>(null);
  // Tagged with their week, so a slow answer for a week left behind never shows.
  const [panels, setPanels] = useState<{ week: string; list: WeekPanel[] } | null>(null);
  const [members, setMembers] = useState<Person[]>([]);
  // Ingredients plan as foods and give a planned food its nutrition when it is logged (ADR-0026).
  const [ingredients, setIngredients] = useState<CatalogIngredient[]>([]);
  const [error, setError] = useState<string | null>(null);

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

  const addIngredient = useCallback((created: CatalogIngredient) => {
    setIngredients((current) => [...current, created]);
  }, []);

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

  return {
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
  };
}
