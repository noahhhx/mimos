import type { PanelAction, PanelBlock, WeekPanel } from "@mimos/plugin-sdk";

import { COUNTRIES, type Continent, type Country, countryByCode, flag } from "./countries.ts";

/**
 * The wheel as pure functions of one user's ledger (ADR-0017). `step`
 * takes an action, decides the write it implies, and returns the week's
 * next state; `panel` renders a state. The store executes the write.
 */

export type Rng = () => number;

/** What the plugin remembers about one user. */
export type Ledger = {
  /** Countries chosen for a week, oldest choice first. */
  choices: readonly { week: string; country: Country }[];
  removed: ReadonlySet<string>;
};

export const EMPTY_LEDGER: Ledger = { choices: [], removed: new Set() };

/** The one write an action can make, scoped to the request's week. */
export type Effect =
  | { kind: "choose"; country: Country }
  | { kind: "unchoose" }
  | { kind: "remove"; country: Country }
  | { kind: "restore" };

/** Why a continent is or is not on this spin's wheel. */
export type ContinentRule =
  | { kind: "none" }
  | { kind: "sits-out"; continent: Continent; last: Country }
  | { kind: "all-left"; continent: Continent; last: Country };

export type Pool = {
  /** Not chosen for any week and not removed. */
  available: readonly Country[];
  /** What a spin may land on: `available` after the continent rule. */
  eligible: readonly Country[];
  rule: ContinentRule;
};

export type Spin = { segments: readonly Country[]; landing: number };

export type WeekState =
  | { kind: "ready"; pool: Pool; segments: readonly Country[] }
  | { kind: "landed"; spin: Spin }
  | { kind: "locked"; country: Country }
  | { kind: "exhausted"; removed: number };

export const MAX_SEGMENTS = 24;

export function chosenFor(ledger: Ledger, week: string): Country | null {
  return ledger.choices.find((choice) => choice.week === week)?.country ?? null;
}

export function poolOf(ledger: Ledger): Pool {
  const used = new Set(ledger.choices.map((choice) => choice.country.code));
  const available = COUNTRIES.filter((country) => !used.has(country.code) && !ledger.removed.has(country.code));
  const last = ledger.choices.at(-1)?.country;
  if (last === undefined) {
    return { available, eligible: available, rule: { kind: "none" } };
  }
  const elsewhere = available.filter((country) => country.continent !== last.continent);
  if (elsewhere.length === 0) {
    return { available, eligible: available, rule: { kind: "all-left", continent: last.continent, last } };
  }
  return { available, eligible: elsewhere, rule: { kind: "sits-out", continent: last.continent, last } };
}

/** Up to `MAX_SEGMENTS` countries from `pool`, in random order. */
export function sample(pool: readonly Country[], rng: Rng): Country[] {
  const shuffled = [...pool];
  const count = Math.min(MAX_SEGMENTS, shuffled.length);
  for (let i = 0; i < count; i++) {
    const j = i + Math.floor(rng() * (shuffled.length - i));
    [shuffled[i], shuffled[j]] = [shuffled[j] as Country, shuffled[i] as Country];
  }
  return shuffled.slice(0, count);
}

/** A random wheel from a non-empty pool and the segment it stops on: every country in the pool is equally likely. */
export function spin(pool: readonly Country[], rng: Rng): Spin {
  const segments = sample(pool, rng);
  return { segments, landing: Math.floor(rng() * segments.length) };
}

export function landedOn(spinResult: Spin): Country {
  return spinResult.segments[spinResult.landing] as Country;
}

export function apply(ledger: Ledger, week: string, effect: Effect): Ledger {
  switch (effect.kind) {
    case "choose":
      return { ...ledger, choices: [...ledger.choices, { week, country: effect.country }] };
    case "unchoose":
      return { ...ledger, choices: ledger.choices.filter((choice) => choice.week !== week) };
    case "remove":
      return { ...ledger, removed: new Set([...ledger.removed, effect.country.code]) };
    case "restore":
      return { ...ledger, removed: new Set() };
  }
}

/** A locked or exhausted week, which no spin changes; otherwise the pool to spin from. */
function settled(ledger: Ledger, week: string): WeekState | Pool {
  const chosen = chosenFor(ledger, week);
  if (chosen !== null) {
    return { kind: "locked", country: chosen };
  }
  const pool = poolOf(ledger);
  return pool.eligible.length === 0 ? { kind: "exhausted", removed: ledger.removed.size } : pool;
}

/** The week as it stands, without spinning. */
function view(ledger: Ledger, week: string, rng: Rng): WeekState {
  const standing = settled(ledger, week);
  return "kind" in standing ? standing : { kind: "ready", pool: standing, segments: sample(standing.eligible, rng) };
}

/** Spins an unlocked week, leaving out `skipped` when anything else can come up. */
function spun(ledger: Ledger, week: string, rng: Rng, skipped?: string): WeekState {
  const standing = settled(ledger, week);
  if ("kind" in standing) {
    return standing;
  }
  const others = standing.eligible.filter((country) => country.code !== skipped);
  return { kind: "landed", spin: spin(others.length > 0 ? others : standing.eligible, rng) };
}

export type Step = { effect: Effect | null; state: WeekState };

/**
 * Acts on the week, then reports its state. Without an action nothing is
 * written. Action ids and values come from the user's browser, so an
 * action that does not fit the ledger writes nothing and re-renders.
 */
export function step(ledger: Ledger, week: string, action: PanelAction | undefined, rng: Rng): Step {
  const unchanged = (): Step => ({ effect: null, state: view(ledger, week, rng) });
  const unlocked = chosenFor(ledger, week) === null;
  const target = action?.value === undefined ? undefined : countryByCode(action.value);
  switch (action?.id) {
    case "spin":
      return { effect: null, state: spun(ledger, week, rng) };
    case "skip":
      return { effect: null, state: spun(ledger, week, rng, action.value) };
    case "choose": {
      if (!unlocked || target === undefined || !poolOf(ledger).eligible.includes(target)) {
        return unchanged();
      }
      return { effect: { kind: "choose", country: target }, state: { kind: "locked", country: target } };
    }
    case "remove": {
      if (!unlocked || target === undefined || !poolOf(ledger).available.includes(target)) {
        return unchanged();
      }
      const effect: Effect = { kind: "remove", country: target };
      return { effect, state: spun(apply(ledger, week, effect), week, rng) };
    }
    case "change": {
      if (unlocked) {
        return unchanged();
      }
      const effect: Effect = { kind: "unchoose" };
      return { effect, state: view(apply(ledger, week, effect), week, rng) };
    }
    case "restore": {
      if (ledger.removed.size === 0) {
        return unchanged();
      }
      const effect: Effect = { kind: "restore" };
      return { effect, state: view(apply(ledger, week, effect), week, rng) };
    }
    default:
      return unchanged();
  }
}

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`;
}

function ruleText(rule: ContinentRule): string {
  switch (rule.kind) {
    case "none":
      return "Spin to find this week's country.";
    case "sits-out":
      return `${rule.continent} sits this one out after your last pick, ${rule.last.name}.`;
    case "all-left":
      return `Everything left is in ${rule.continent}, so it stays in after your last pick, ${rule.last.name}.`;
  }
}

/** A wheel needs two segments; with one country left there is nothing to spin through. */
function wheel(segments: readonly Country[], landing?: number): PanelBlock[] {
  if (segments.length < 2) {
    return [];
  }
  return [
    {
      type: "wheel",
      segments: segments.map((country) => ({ label: country.name, icon: flag(country) })),
      ...(landing === undefined ? {} : { landing }),
    },
  ];
}

export function panel(state: WeekState): WeekPanel {
  switch (state.kind) {
    case "ready":
      return {
        blocks: [
          {
            type: "text",
            text: `${plural(state.pool.available.length, "country", "countries")} left. ${ruleText(state.pool.rule)}`,
          },
          ...wheel(state.segments),
          { type: "actions", actions: [{ id: "spin", label: "Spin", primary: true }] },
        ],
      };
    case "landed": {
      const country = landedOn(state.spin);
      return {
        blocks: [
          ...wheel(state.spin.segments, state.spin.landing),
          { type: "highlight", icon: flag(country), title: country.name, text: country.continent },
          {
            type: "actions",
            actions: [
              { id: "choose", label: `Choose ${country.name}`, value: country.code, primary: true },
              { id: "skip", label: "Skip", value: country.code },
              { id: "remove", label: "Remove from wheel", value: country.code },
            ],
          },
        ],
      };
    }
    case "locked":
      return {
        summary: { icon: flag(state.country), label: state.country.name },
        blocks: [
          { type: "highlight", icon: flag(state.country), title: state.country.name, text: state.country.continent },
          {
            type: "text",
            text: `${state.country.cuisine} recipes from the library show up under Suggestions when there are any.`,
          },
          { type: "actions", actions: [{ id: "change", label: "Change country" }] },
        ],
      };
    case "exhausted":
      if (state.removed === 0) {
        return {
          blocks: [{ type: "text", text: "Every country has had its week. You cooked your way around the world." }],
        };
      }
      return {
        blocks: [
          {
            type: "text",
            text: `No countries left to spin. You removed ${plural(state.removed, "country", "countries")} from the wheel; put them back to keep going.`,
          },
          { type: "actions", actions: [{ id: "restore", label: "Put removed countries back", primary: true }] },
        ],
      };
  }
}
