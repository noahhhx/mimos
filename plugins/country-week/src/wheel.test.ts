import assert from "node:assert/strict";
import { test } from "node:test";

import type { PanelAction, WeekPanel } from "@mimos/plugin-sdk";

import { COUNTRIES, type Country, countryByCode } from "./countries.ts";
import {
  apply,
  EMPTY_LEDGER,
  type Ledger,
  landedOn,
  MAX_SEGMENTS,
  panel,
  poolOf,
  type Rng,
  spin,
  step,
  type WeekState,
} from "./wheel.ts";

const WEEK = "2026-10-05";
const LAST_WEEK = "2026-09-28";
const NEXT_WEEK = "2026-10-12";

/** mulberry32: a small seeded generator, so every spin in these tests repeats. */
function seeded(seed: number): Rng {
  let a = seed;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function country(code: string): Country {
  const found = countryByCode(code);
  assert.ok(found, code);
  return found;
}

function ledger(choices: Array<[week: string, code: string]>, removed: string[] = []): Ledger {
  return { choices: choices.map(([week, code]) => ({ week, country: country(code) })), removed: new Set(removed) };
}

function act(l: Ledger, action?: PanelAction, week = WEEK, seed = 1) {
  return step(l, week, action, seeded(seed));
}

function landed(state: WeekState): Country {
  assert.equal(state.kind, "landed");
  return landedOn(state.spin);
}

/** Every limit the extension contract (1.1.0) puts on a panel, plus the no-em-dash copy rule. */
function assertWithinContract(p: WeekPanel): void {
  assert.ok(p.blocks.length <= 8, "at most 8 blocks");
  assert.ok(!JSON.stringify(p).includes("—"), "no em-dashes");
  if (p.summary) {
    assert.ok(p.summary.label.length <= 60);
    assert.ok((p.summary.icon ?? "").length <= 8);
  }
  for (const block of p.blocks) {
    switch (block.type) {
      case "text":
        assert.ok(block.text.length <= 280, block.text);
        break;
      case "highlight":
        assert.ok(block.title.length <= 80 && (block.text ?? "").length <= 200 && (block.icon ?? "").length <= 8);
        break;
      case "wheel":
        assert.ok(block.segments.length >= 2 && block.segments.length <= 60);
        for (const segment of block.segments) {
          assert.ok(segment.label.length <= 60 && (segment.icon ?? "").length <= 8);
        }
        if (block.landing !== undefined) {
          assert.ok(block.landing >= 0 && block.landing < block.segments.length);
        }
        break;
      case "actions":
        assert.ok(block.actions.length >= 1 && block.actions.length <= 4);
        assert.ok(block.actions.filter((action) => action.primary).length <= 1, "one primary button");
        for (const action of block.actions) {
          assert.match(action.id, /^[a-z0-9][a-z0-9-]{0,39}$/);
          assert.ok(action.label.length <= 40, action.label);
          assert.ok((action.value ?? "").length <= 100);
        }
        break;
    }
  }
}

function buttons(p: WeekPanel) {
  return p.blocks.flatMap((block) => (block.type === "actions" ? block.actions : []));
}

function texts(p: WeekPanel): string[] {
  return p.blocks.flatMap((block) => (block.type === "text" ? [block.text] : []));
}

// Eligibility

test("a fresh wheel offers every country, with no continent sitting out", () => {
  const pool = poolOf(EMPTY_LEDGER);
  assert.equal(pool.available.length, 197);
  assert.equal(pool.eligible.length, 197);
  assert.deepEqual(pool.rule, { kind: "none" });
});

test("a country chosen for any week, or removed, never comes up again", () => {
  const pool = poolOf(ledger([[LAST_WEEK, "IT"], [NEXT_WEEK, "PE"]], ["KE"]));
  const codes = new Set(pool.available.map((c) => c.code));
  assert.ok(!codes.has("IT") && !codes.has("PE") && !codes.has("KE"));
  assert.equal(pool.available.length, 194);
});

test("the most recently chosen country's continent sits out, by choice order rather than week order", () => {
  // Peru was chosen first for a later week, Italy afterwards for an earlier one: Italy is the last pick.
  const pool = poolOf(ledger([[NEXT_WEEK, "PE"], [LAST_WEEK, "IT"]]));
  assert.deepEqual(pool.rule, { kind: "sits-out", continent: "Europe", last: country("IT") });
  assert.ok(pool.eligible.every((c) => c.continent !== "Europe"));
  assert.ok(pool.eligible.some((c) => c.continent === "South America"));
  assert.equal(pool.eligible.length, 197 - 2 - (45 - 1));
});

test("when only the last pick's continent is left, it stays in", () => {
  const notEurope = COUNTRIES.filter((c) => c.continent !== "Europe" && c.code !== "PE").map((c) => c.code);
  const pool = poolOf(ledger([[LAST_WEEK, "PE"], [NEXT_WEEK, "IT"]], notEurope));
  assert.deepEqual(pool.rule, { kind: "all-left", continent: "Europe", last: country("IT") });
  assert.equal(pool.eligible.length, 44);
  assert.ok(pool.eligible.every((c) => c.continent === "Europe"));
});

// Spinning

test("a spin repeats for the same seed and lands on a segment of the pool", () => {
  const pool = poolOf(EMPTY_LEDGER).eligible;
  const first = spin(pool, seeded(42));
  assert.deepEqual(spin(pool, seeded(42)), first);
  assert.equal(first.segments.length, MAX_SEGMENTS);
  assert.equal(new Set(first.segments).size, MAX_SEGMENTS, "no repeated segments");
  assert.ok(first.segments.every((c) => pool.includes(c)));
  assert.ok(first.landing >= 0 && first.landing < first.segments.length);
});

test("a small pool fills the wheel with exactly its countries", () => {
  const pool = [country("PE"), country("JP"), country("KE")];
  const result = spin(pool, seeded(7));
  assert.deepEqual(new Set(result.segments), new Set(pool));
});

test("every country in the pool can come up, not just the first 24", () => {
  const pool = poolOf(EMPTY_LEDGER).eligible;
  const seen = new Set<string>();
  for (let seed = 0; seed < 5000; seed++) {
    seen.add(landedOn(spin(pool, seeded(seed))).code);
  }
  assert.equal(seen.size, pool.length);
});

// The state machine

test("rendering without an action writes nothing, in every state", () => {
  const locked = ledger([[WEEK, "IT"]]);
  const exhausted = ledger([], COUNTRIES.map((c) => c.code));
  for (const [l, kind] of [
    [EMPTY_LEDGER, "ready"],
    [locked, "locked"],
    [exhausted, "exhausted"],
  ] as const) {
    const result = act(l);
    assert.equal(result.effect, null);
    assert.equal(result.state.kind, kind);
  }
});

test("spin lands on an eligible country and writes nothing", () => {
  const l = ledger([[LAST_WEEK, "IT"]]);
  const result = act(l, { id: "spin" });
  assert.equal(result.effect, null);
  assert.ok(poolOf(l).eligible.includes(landed(result.state)));
});

test("spin on a locked week keeps it locked", () => {
  const result = act(ledger([[WEEK, "IT"]]), { id: "spin" });
  assert.deepEqual(result, { effect: null, state: { kind: "locked", country: country("IT") } });
});

test("choose locks the week to the country", () => {
  const result = act(EMPTY_LEDGER, { id: "choose", value: "PE" });
  assert.deepEqual(result.effect, { kind: "choose", country: country("PE") });
  assert.deepEqual(result.state, { kind: "locked", country: country("PE") });
});

test("a forged or stale choose writes nothing", () => {
  const l = ledger([[LAST_WEEK, "IT"], [NEXT_WEEK, "JP"]], ["KE"]);
  const forged: PanelAction[] = [
    { id: "choose" },
    { id: "choose", value: "ZZ" },
    { id: "choose", value: "pe" },
    { id: "choose", value: "JP" }, // chosen for another week
    { id: "choose", value: "KE" }, // removed
    { id: "choose", value: "IN" }, // Asia sits out after Japan
  ];
  for (const action of forged) {
    const result = act(l, action);
    assert.equal(result.effect, null, JSON.stringify(action));
    assert.equal(result.state.kind, "ready");
  }
  const locked = act(ledger([[WEEK, "IT"]]), { id: "choose", value: "PE" });
  assert.deepEqual(locked, { effect: null, state: { kind: "locked", country: country("IT") } });
});

test("skip spins again and avoids the skipped country while anything else can come up", () => {
  const peruOrKenya = ledger([], COUNTRIES.filter((c) => c.code !== "PE" && c.code !== "KE").map((c) => c.code));
  for (let seed = 0; seed < 20; seed++) {
    assert.equal(landed(act(peruOrKenya, { id: "skip", value: "PE" }, WEEK, seed).state).code, "KE");
  }
  const onlyPeru = ledger([], COUNTRIES.filter((c) => c.code !== "PE").map((c) => c.code));
  const result = act(onlyPeru, { id: "skip", value: "PE" });
  assert.equal(result.effect, null);
  assert.equal(landed(result.state).code, "PE");
});

test("remove takes the country off the wheel for good, then spins again", () => {
  for (let seed = 0; seed < 50; seed++) {
    const result = act(EMPTY_LEDGER, { id: "remove", value: "PE" }, WEEK, seed);
    assert.deepEqual(result.effect, { kind: "remove", country: country("PE") });
    assert.notEqual(landed(result.state).code, "PE");
  }
});

test("a forged remove writes nothing", () => {
  const l = ledger([[LAST_WEEK, "IT"]], ["KE"]);
  for (const action of [
    { id: "remove" },
    { id: "remove", value: "ZZ" },
    { id: "remove", value: "IT" },
    { id: "remove", value: "KE" },
  ]) {
    assert.equal(act(l, action).effect, null, JSON.stringify(action));
  }
  assert.equal(act(ledger([[WEEK, "IT"]]), { id: "remove", value: "PE" }).effect, null, "locked week");
});

test("change unlocks the week and puts the country back on the wheel", () => {
  const l = ledger([[LAST_WEEK, "PE"], [WEEK, "IT"]]);
  const result = act(l, { id: "change" });
  assert.deepEqual(result.effect, { kind: "unchoose" });
  assert.equal(result.state.kind, "ready");
  const after = apply(l, WEEK, { kind: "unchoose" });
  assert.ok(poolOf(after).available.includes(country("IT")));
  assert.deepEqual(poolOf(after).rule, { kind: "sits-out", continent: "South America", last: country("PE") });
  assert.equal(act(EMPTY_LEDGER, { id: "change" }).effect, null, "nothing to change");
});

test("restore puts removed countries back, and only when there are some", () => {
  const l = ledger([], ["PE", "KE"]);
  const result = act(l, { id: "restore" });
  assert.deepEqual(result.effect, { kind: "restore" });
  assert.equal(poolOf(apply(l, WEEK, { kind: "restore" })).available.length, 197);
  assert.equal(act(EMPTY_LEDGER, { id: "restore" }).effect, null);
});

test("an unknown action re-renders", () => {
  assert.equal(act(EMPTY_LEDGER, { id: "delete-everything", value: "PE" }).effect, null);
});

// Panels

test("ready says how many countries are left and which continent sits out", () => {
  const fresh = panel(act(EMPTY_LEDGER).state);
  assert.deepEqual(texts(fresh), ["197 countries left. Spin to find this week's country."]);
  assert.deepEqual(buttons(fresh), [{ id: "spin", label: "Spin", primary: true }]);
  assert.equal(fresh.summary, undefined);
  const wheel = fresh.blocks.find((block) => block.type === "wheel");
  assert.ok(wheel && wheel.type === "wheel");
  assert.equal(wheel.landing, undefined);
  assert.equal(wheel.segments.length, MAX_SEGMENTS);

  const afterItaly = panel(act(ledger([[LAST_WEEK, "IT"]])).state);
  assert.deepEqual(texts(afterItaly), ["196 countries left. Europe sits this one out after your last pick, Italy."]);
});

test("landed shows where the wheel stops and offers choose, skip, and remove", () => {
  const state = act(EMPTY_LEDGER, { id: "spin" }).state;
  const stop = landed(state);
  const p = panel(state);
  const wheel = p.blocks[0];
  assert.ok(wheel?.type === "wheel");
  assert.equal(wheel.segments[wheel.landing ?? -1]?.label, stop.name);
  assert.deepEqual(p.blocks[1], {
    type: "highlight",
    icon: wheel.segments[wheel.landing ?? -1]?.icon,
    title: stop.name,
    text: stop.continent,
  });
  assert.deepEqual(buttons(p), [
    { id: "choose", label: `Choose ${stop.name}`, value: stop.code, primary: true },
    { id: "skip", label: "Skip", value: stop.code },
    { id: "remove", label: "Remove from wheel", value: stop.code },
  ]);
  assert.equal(p.summary, undefined);
});

test("a locked week shows its flag in the summary and offers to change", () => {
  const p = panel({ kind: "locked", country: country("PE") });
  assert.deepEqual(p.summary, { icon: "\u{1F1F5}\u{1F1EA}", label: "Peru" });
  assert.deepEqual(texts(p), ["Peruvian recipes from the library show up under Suggestions when there are any."]);
  assert.deepEqual(buttons(p), [{ id: "change", label: "Change country" }]);
});

test("an exhausted wheel offers the removed countries back, if any", () => {
  const withRemovals = panel({ kind: "exhausted", removed: 12 });
  assert.deepEqual(buttons(withRemovals), [{ id: "restore", label: "Put removed countries back", primary: true }]);
  assert.match(texts(withRemovals)[0] ?? "", /You removed 12 countries/);
  const allChosen = panel({ kind: "exhausted", removed: 0 });
  assert.deepEqual(buttons(allChosen), []);
});

test("every panel the plugin can render fits the contract", () => {
  for (const c of COUNTRIES) {
    assertWithinContract(panel({ kind: "locked", country: c }));
    const others = COUNTRIES.filter((other) => other !== c).slice(0, MAX_SEGMENTS - 1);
    assertWithinContract(panel({ kind: "landed", spin: { segments: [c, ...others], landing: 0 } }));
    assertWithinContract(panel(act(ledger([[LAST_WEEK, c.code]])).state));
  }
  assertWithinContract(panel(act(EMPTY_LEDGER).state));
  assertWithinContract(panel({ kind: "exhausted", removed: 0 }));
  assertWithinContract(panel({ kind: "exhausted", removed: 197 }));
  const oneLeft = ledger([], COUNTRIES.filter((c) => c.code !== "PE").map((c) => c.code));
  assertWithinContract(panel(act(oneLeft).state));
  assertWithinContract(panel(act(oneLeft, { id: "spin" }).state));
});
