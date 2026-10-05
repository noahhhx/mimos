import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, test } from "node:test";

import { countryByCode, type Country } from "./countries.ts";
import { openStore } from "./store.ts";

const ALICE = "6f1c1d2e-0000-4000-8000-000000000001";
const BOB = "6f1c1d2e-0000-4000-8000-000000000002";

function country(code: string): Country {
  const found = countryByCode(code);
  assert.ok(found, code);
  return found;
}

const dir = mkdtempSync(join(tmpdir(), "country-week-"));
after(() => {
  rmSync(dir, { recursive: true, force: true });
});

test("choices come back in the order they were made, per subject", () => {
  const store = openStore(":memory:");
  store.apply(ALICE, "2026-10-12", { kind: "choose", country: country("PE") });
  store.apply(ALICE, "2026-10-05", { kind: "choose", country: country("IT") });
  store.apply(BOB, "2026-10-05", { kind: "choose", country: country("JP") });
  assert.deepEqual(
    store.ledger(ALICE).choices.map((choice) => [choice.week, choice.country.code]),
    [
      ["2026-10-12", "PE"],
      ["2026-10-05", "IT"],
    ],
  );
  assert.deepEqual(
    store.ledger(BOB).choices.map((choice) => choice.country.code),
    ["JP"],
  );
  store.close();
});

test("unchoose frees the week and the country; a repeated write changes nothing", () => {
  const store = openStore(":memory:");
  store.apply(ALICE, "2026-10-05", { kind: "choose", country: country("IT") });
  store.apply(ALICE, "2026-10-05", { kind: "choose", country: country("PE") });
  assert.deepEqual(
    store.ledger(ALICE).choices.map((choice) => choice.country.code),
    ["IT"],
  );
  store.apply(ALICE, "2026-10-05", { kind: "unchoose" });
  store.apply(ALICE, "2026-10-05", { kind: "unchoose" });
  assert.deepEqual(store.ledger(ALICE).choices, []);
  store.apply(ALICE, "2026-10-12", { kind: "choose", country: country("IT") });
  assert.equal(store.ledger(ALICE).choices[0]?.week, "2026-10-12");
  store.close();
});

test("remove and restore touch only the subject's own removals", () => {
  const store = openStore(":memory:");
  store.apply(ALICE, "2026-10-05", { kind: "remove", country: country("KE") });
  store.apply(ALICE, "2026-10-05", { kind: "remove", country: country("KE") });
  store.apply(BOB, "2026-10-05", { kind: "remove", country: country("PE") });
  assert.deepEqual([...store.ledger(ALICE).removed], ["KE"]);
  store.apply(ALICE, "2026-10-05", { kind: "restore" });
  assert.deepEqual([...store.ledger(ALICE).removed], []);
  assert.deepEqual([...store.ledger(BOB).removed], ["PE"]);
  store.close();
});

test("a database file keeps every wheel across restarts", () => {
  const path = join(dir, "country-week.db");
  const first = openStore(path);
  first.apply(ALICE, "2026-10-05", { kind: "choose", country: country("PE") });
  first.apply(ALICE, "2026-10-05", { kind: "remove", country: country("KE") });
  first.close();
  const second = openStore(path);
  const ledger = second.ledger(ALICE);
  assert.deepEqual(
    ledger.choices.map((choice) => choice.country.code),
    ["PE"],
  );
  assert.deepEqual([...ledger.removed], ["KE"]);
  second.close();
});
