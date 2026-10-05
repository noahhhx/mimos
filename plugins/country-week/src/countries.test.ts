import assert from "node:assert/strict";
import { test } from "node:test";

import { CONTINENTS, COUNTRIES, countryByCode, flag } from "./countries.ts";

test("the wheel holds the 193 UN members plus Vatican City, Palestine, Taiwan, and Kosovo", () => {
  assert.equal(COUNTRIES.length, 197);
  for (const code of ["VA", "PS", "TW", "XK"]) {
    assert.ok(countryByCode(code), code);
  }
  const perContinent = Object.fromEntries(
    CONTINENTS.map((continent) => [continent, COUNTRIES.filter((c) => c.continent === continent).length]),
  );
  assert.deepEqual(perContinent, {
    Africa: 54,
    Asia: 49,
    Europe: 45,
    "North America": 23,
    "South America": 12,
    Oceania: 14,
  });
});

test("codes are unique two-letter uppercase, and names are unique", () => {
  assert.equal(new Set(COUNTRIES.map((c) => c.code)).size, COUNTRIES.length);
  assert.equal(new Set(COUNTRIES.map((c) => c.name)).size, COUNTRIES.length);
  for (const country of COUNTRIES) {
    assert.match(country.code, /^[A-Z]{2}$/);
    assert.ok(CONTINENTS.includes(country.continent), country.name);
    assert.ok(country.tags.length > 0, country.name);
  }
});

test("M49 edge cases land on the agreed continents", () => {
  const continentOf = (code: string) => countryByCode(code)?.continent;
  assert.equal(continentOf("TR"), "Asia");
  assert.equal(continentOf("CY"), "Asia");
  assert.equal(continentOf("GE"), "Asia");
  assert.equal(continentOf("RU"), "Europe");
  assert.equal(continentOf("PA"), "North America");
  assert.equal(continentOf("JM"), "North America");
  assert.equal(continentOf("EG"), "Africa");
});

test("countries without a hand-tuned matcher match their cuisine tag", () => {
  assert.deepEqual(countryByCode("PE")?.tags, ["peruvian"]);
  assert.deepEqual(countryByCode("CR")?.tags, ["costa-rican"]);
  assert.deepEqual(countryByCode("LB")?.tags, ["lebanese", "middle-eastern"]);
});

test("countryByCode rejects anything but a known uppercase code", () => {
  assert.equal(countryByCode("pe"), undefined);
  assert.equal(countryByCode("ZZ"), undefined);
  assert.equal(countryByCode(""), undefined);
});

test("flag spells the code in regional indicator symbols", () => {
  const peru = countryByCode("PE");
  assert.ok(peru);
  assert.equal(flag(peru), "\u{1F1F5}\u{1F1EA}");
  for (const country of COUNTRIES) {
    assert.equal(flag(country).length, 4, `${country.code} fits an 8-unit icon`);
  }
});

test("user-facing country copy has no em-dashes", () => {
  for (const country of COUNTRIES) {
    assert.ok(!`${country.name} ${country.cuisine}`.includes("—"), country.code);
  }
});
