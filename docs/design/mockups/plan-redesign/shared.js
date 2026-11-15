// Shared sample data, state, and helpers for the plan redesign mockups.
// Nothing here talks to the API: every button changes this in-memory week.

const ME = "n";
const MEMBERS = [
  { id: "n", name: "Noah", chip: "NO", you: true },
  { id: "s", name: "Sam", chip: "SA" },
];
const MEAL_TYPES = ["BREAKFAST", "LUNCH", "DINNER", "SNACK"];
const MEAL_LABEL = { BREAKFAST: "Breakfast", LUNCH: "Lunch", DINNER: "Dinner", SNACK: "Snack" };
const TODAY = "2026-11-14";

let nextId = 1;
// kcal/p/c/f are the caller's share, as "Planned" counts them.
const e = (meal, title, servings, diners, kcal, p, c, f, extra = {}) => ({
  id: "e" + nextId++,
  meal,
  title,
  kind: "recipe",
  servings,
  diners,
  kcal,
  p,
  c,
  f,
  logged: false,
  ...extra,
});
const food = (meal, title, amount, unit, kcal, p, c, f, extra = {}) =>
  e(meal, title, amount, [ME], kcal, p, c, f, { kind: "food", unit, ...extra });
const L = { logged: true };

const SAMPLE = [
  {
    date: "2026-11-09",
    target: { kcal: 3120, p: 125, c: 430, f: 95 },
    training: [{ id: "t1", sport: "Ride", min: 60, intensity: "Endurance", source: "intervals.icu", done: true }],
    entries: [
      e("BREAKFAST", "Overnight Oats", 1, [ME], 480, 18, 70, 14, L),
      e("LUNCH", "Greek Salad", 1, [ME], 420, 14, 22, 30, L),
      e("DINNER", "Miso Salmon Rice Bowl", 2, [ME, "s"], 690, 42, 78, 22, L),
      food("SNACK", "Banana", 120, "g", 107, 1, 27, 0, L),
    ],
  },
  {
    date: "2026-11-10",
    target: { kcal: 3050, p: 125, c: 420, f: 95 },
    training: [{ id: "t2", sport: "Run", min: 45, intensity: "Tempo", source: "By hand", done: true }],
    entries: [
      e("BREAKFAST", "Fluffy Buttermilk Pancakes", 2, [ME, "s"], 520, 14, 80, 16, L),
      e("LUNCH", "Mujadara", 1, [ME], 560, 20, 88, 14, L),
      e("DINNER", "Beef Chili", 4, [ME, "s"], 820, 56, 70, 32, L),
      food("SNACK", "Greek Yogurt", 200, "g", 194, 20, 8, 10, L),
    ],
  },
  {
    date: "2026-11-11",
    target: { kcal: 2650, p: 125, c: 330, f: 90 },
    training: [],
    entries: [
      e("BREAKFAST", "Overnight Oats", 1, [ME], 480, 18, 70, 14, L),
      e("LUNCH", "Beef Chili", 1, [ME], 410, 28, 35, 16, L),
      e("LUNCH", "Tuna Melt", 1, ["s"], 0, 0, 0, 0),
      e("DINNER", "Red Lentil Dahl", 2, [ME, "s"], 460, 22, 68, 10, L),
    ],
  },
  {
    date: "2026-11-12",
    target: { kcal: 3650, p: 125, c: 560, f: 100 },
    training: [
      { id: "t3", sport: "Ride", min: 120, intensity: "Endurance", source: "intervals.icu", done: true },
      { id: "t4", sport: "Strength", min: 30, intensity: "Moderate", source: "By hand", done: true },
    ],
    entries: [
      e("BREAKFAST", "Porridge with Honey", 1, [ME], 520, 16, 92, 10, L),
      e("LUNCH", "Chicken Wrap", 1, [ME], 610, 40, 62, 20, L),
      e("DINNER", "Pasta e Fagioli", 3, [ME, "s"], 780, 32, 120, 18, L),
      e("SNACK", "Oat Flapjack", 1, [ME], 390, 5, 50, 19),
    ],
  },
  {
    date: "2026-11-13",
    target: { kcal: 2650, p: 125, c: 330, f: 90 },
    training: [],
    entries: [
      e("BREAKFAST", "Overnight Oats", 1, [ME], 480, 18, 70, 14, L),
      e("DINNER", "Margherita Pizza", 2, [ME, "s"], 850, 34, 104, 30, L),
    ],
  },
  {
    date: "2026-11-14",
    target: { kcal: 4300, p: 125, c: 700, f: 105 },
    training: [{ id: "t5", sport: "Ride", min: 180, intensity: "Endurance", source: "intervals.icu", done: false }],
    entries: [
      e("BREAKFAST", "Fluffy Buttermilk Pancakes", 2, [ME, "s"], 520, 14, 80, 16, L),
      e("LUNCH", "Jacket Potato with Beans", 1, [ME], 580, 22, 104, 8),
      e("DINNER", "Thai Green Curry", 3, [ME, "s"], 760, 36, 84, 30),
      food("SNACK", "Rice Cakes", 3, "", 105, 2, 24, 1),
    ],
  },
  {
    date: "2026-11-15",
    target: { kcal: 3250, p: 125, c: 460, f: 95 },
    training: [{ id: "t6", sport: "Run", min: 60, intensity: "Endurance", source: "intervals.icu", done: false }],
    entries: [
      e("BREAKFAST", "Eggs on Toast", 1, ["s"], 0, 0, 0, 0),
      e("DINNER", "Roast Chicken with Vegetables", 4, [ME, "s"], 980, 72, 60, 48),
    ],
  },
];

const RECIPES = [
  ["Overnight Oats", 480, 18, 70, 14, false],
  ["Spaghetti alla Puttanesca", 640, 22, 98, 18, true],
  ["Risotto ai Funghi", 590, 16, 92, 16, true],
  ["Red Lentil Dahl", 460, 22, 68, 10, false],
  ["Chicken Wrap", 610, 40, 62, 20, false],
  ["Thai Green Curry", 760, 36, 84, 30, false],
  ["Greek Yogurt", 194, 20, 8, 10, "200 g"],
  ["Banana", 107, 1, 27, 0, "120 g"],
];

const state = {
  view: localStorage.getItem("mock.plan.view") || "mine",
  days: structuredClone(SAMPLE),
};

// ---------- formatting ----------
const parse = (iso) => {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
};
const dayShort = (iso) => parse(iso).toLocaleDateString("en-GB", { weekday: "short" });
const dayLong = (iso) => parse(iso).toLocaleDateString("en-GB", { weekday: "long" });
const dayNum = (iso) => parse(iso).getDate();
const dayLabel = (iso) => parse(iso).toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric" });
const dayFull = (iso) => parse(iso).toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" });
const isPast = (iso) => iso < TODAY;
const n = (x) => Math.round(x).toLocaleString("en-GB");

const servingsText = (en) =>
  en.kind === "food"
    ? en.unit
      ? `${n(en.servings)} ${en.unit}`
      : `${n(en.servings)} pieces`
    : `${formatNum(en.servings)} serving${en.servings === 1 ? "" : "s"}`;
const formatNum = (x) => (Number.isInteger(x) ? String(x) : x.toFixed(1));
const shareText = (en) =>
  en.diners.length > 1 && en.kind === "recipe"
    ? `your share ${formatNum(Math.round((en.servings / en.diners.length) * 10) / 10)} of ${formatNum(en.servings)}`
    : servingsText(en);
const sessionShort = (s) => `${s.sport} ${s.min}′`;
const sessionLong = (s) => `${s.sport} · ${s.min} min · ${s.intensity}`;
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

// ---------- queries ----------
const shared = () => MEMBERS.length > 1;
const eats = (en) => en.diners.includes(ME);
const visible = (day) => (state.view === "mine" ? day.entries.filter(eats) : day.entries);
const inSlot = (day, meal) => visible(day).filter((en) => en.meal === meal);
const showsFuel = () => state.view === "mine";
const planned = (day) =>
  day.entries.filter(eats).reduce(
    (t, en) => ({ kcal: t.kcal + en.kcal, p: t.p + en.p, c: t.c + en.c, f: t.f + en.f }),
    { kcal: 0, p: 0, c: 0, f: 0 },
  );
const findEntry = (id) => {
  for (const day of state.days) {
    const en = day.entries.find((x) => x.id === id);
    if (en) return { day, en };
  }
  return null;
};
const dayOf = (date) => state.days.find((d) => d.date === date);
const memberChips = (en, interactive) =>
  MEMBERS.map((m) => {
    const on = en.diners.includes(m.id);
    const attrs = `class="chip${on ? " on" : ""}" title="${m.name}${m.you ? " (you)" : ""}"`;
    return interactive
      ? `<button ${attrs} aria-pressed="${on}" onclick="act.diner('${en.id}','${m.id}')">${m.chip}</button>`
      : `<span ${attrs}>${m.chip}</span>`;
  }).join(" ");

function fuelTable(day) {
  const t = day.target;
  const p = planned(day);
  return `<table class="fuel">
    <thead><tr><th></th><th>kcal</th><th>Protein</th><th>Carbs</th><th>Fat</th></tr></thead>
    <tbody>
      <tr><th>Fuel for the day</th><td>${n(t.kcal)}</td><td>${t.p} g</td><td>${t.c} g</td><td>${t.f} g</td></tr>
      <tr><th>Planned</th><td>${n(p.kcal)}</td><td>${p.p} g</td><td>${p.c} g</td><td>${p.f} g</td></tr>
    </tbody></table>`;
}

function pluginBits() {
  return `
    <details class="week-panel">
      <summary><span class="name">Country of the Week</span><span class="sum">🇮🇹 Italy</span><span class="sign">+</span></summary>
      <div class="body"><p>Italy landed on the wheel for this week. Pasta, risotto, and a lot of olive oil.</p>
        <button class="button secondary small">Spin again</button></div>
    </details>
    <div class="suggestion">
      <div class="label">Suggestion · via Country of the Week</div>
      <h4>A taste of Italy</h4>
      <ul><li>Spaghetti alla Puttanesca · Fri Dinner</li><li>Risotto ai Funghi · Sun Lunch</li></ul>
      <button class="button small" onclick="act.applySuggestion()">Add to plan</button>
    </div>`;
}

// ---------- actions ----------
const act = {
  servings(id, delta) {
    const f = findEntry(id);
    if (!f) return;
    const step = f.en.kind === "food" ? (f.en.unit ? 10 : 1) : 0.5;
    const next = f.en.servings + delta * step;
    if (next <= 0) return;
    const ratio = next / f.en.servings;
    for (const k of ["kcal", "p", "c", "f"]) f.en[k] = Math.round(f.en[k] * ratio);
    f.en.servings = next;
    rerender();
  },
  remove(id) {
    for (const day of state.days) day.entries = day.entries.filter((x) => x.id !== id);
    if (window.onRemoved) window.onRemoved(id);
    rerender();
  },
  log(id) {
    const f = findEntry(id);
    if (f) f.en.logged = true;
    rerender();
  },
  diner(id, member) {
    const f = findEntry(id);
    if (!f) return;
    const has = f.en.diners.includes(member);
    if (has && f.en.diners.length === 1) return;
    f.en.diners = has ? f.en.diners.filter((x) => x !== member) : [...f.en.diners, member];
    rerender();
  },
  removeSession(date, sid) {
    const day = dayOf(date);
    day.training = day.training.filter((s) => s.id !== sid);
    day.target = { ...day.target, kcal: day.target.kcal - 500, c: day.target.c - 110 };
    rerender();
  },
  addSession(date) {
    const day = dayOf(date);
    day.training.push({ id: "t" + nextId++, sport: "Ride", min: 60, intensity: "Endurance", source: "By hand", done: false });
    day.target = { ...day.target, kcal: day.target.kcal + 500, c: day.target.c + 110 };
    rerender();
  },
  applySuggestion() {
    dayOf("2026-11-13").entries.push(e("LUNCH", "Spaghetti alla Puttanesca", 2, [ME, "s"], 640, 22, 98, 18));
    dayOf("2026-11-15").entries.push(e("LUNCH", "Risotto ai Funghi", 2, [ME, "s"], 590, 16, 92, 16));
    rerender();
  },
};

// ---------- picker ----------
function openPicker(date, meal) {
  const close = () => document.querySelector(".backdrop")?.remove();
  close();
  const wrap = document.createElement("div");
  wrap.className = "backdrop";
  wrap.innerHTML = `<div class="picker" role="dialog" aria-label="Pick a meal">
    <div class="row" style="justify-content:space-between;margin-bottom:12px">
      <span class="label">Add to ${dayLabel(date)}</span>
      <button class="button secondary small" data-close>Close</button></div>
    <div class="row">
      <select aria-label="Meal">${MEAL_TYPES.map((m) => `<option value="${m}" ${m === meal ? "selected" : ""}>${MEAL_LABEL[m]}</option>`).join("")}</select>
      <input type="search" placeholder="Search recipes and ingredients…" autofocus>
      <button class="button secondary">Scan a barcode</button></div>
    <ul>${RECIPES.map(
      (r, i) =>
        `<li><button data-i="${i}"><span class="serif" style="font-size:18px">${r[0]}</span>
         <span class="muted"> · ${typeof r[5] === "string" ? r[5] : r[5] ? "library" : "yours"}</span></button></li>`,
    ).join("")}</ul></div>`;
  wrap.addEventListener("click", (ev) => {
    if (ev.target === wrap || ev.target.closest("[data-close]")) return close();
    const b = ev.target.closest("[data-i]");
    if (!b) return;
    const r = RECIPES[Number(b.dataset.i)];
    const chosen = wrap.querySelector("select").value;
    const add =
      typeof r[5] === "string"
        ? food(chosen, r[0], parseInt(r[5]), "g", r[1], r[2], r[3], r[4])
        : e(chosen, r[0], chosen === "DINNER" ? 2 : 1, chosen === "DINNER" ? [ME, "s"] : [ME], r[1], r[2], r[3], r[4]);
    dayOf(date).entries.push(add);
    close();
    rerender();
  });
  document.body.append(wrap);
  wrap.querySelector("input").focus();
}

// ---------- chrome ----------
const PAGES = [
  ["index.html", "Overview"],
  ["current.html", "Today's page"],
  ["1-ledger.html", "1 · Ledger"],
  ["2-one-day.html", "2 · One day"],
  ["3-plan-eat.html", "3 · Plan / Eat"],
  ["4-fuel-columns.html", "4 · Fuel columns"],
];

function chrome() {
  const here = location.pathname.split("/").pop() || "index.html";
  const theme = localStorage.getItem("mock.theme") || "light";
  document.documentElement.dataset.theme = theme;
  const bar = document.createElement("div");
  bar.className = "mockbar";
  bar.innerHTML = `<strong>Mockup</strong>
    ${PAGES.map(([href, label]) => `<a href="${href}" class="${href === here ? "on" : ""}">${label}</a>`).join("")}
    <span class="spacer"></span>
    <span>Theme</span>
    <button data-theme-set="light" aria-pressed="${theme === "light"}">Light</button>
    <button data-theme-set="dark" aria-pressed="${theme === "dark"}">Dark</button>
    ${
      window.render
        ? `<span>Household view</span>
    <button data-view="mine" aria-pressed="${state.view === "mine"}">Mine</button>
    <button data-view="everyone" aria-pressed="${state.view === "everyone"}">Everyone</button>
    <button data-reset>Reset data</button>`
        : ""
    }`;
  bar.addEventListener("click", (ev) => {
    const t = ev.target.closest("button");
    if (!t) return;
    if (t.dataset.themeSet) {
      localStorage.setItem("mock.theme", t.dataset.themeSet);
      location.reload();
    } else if (t.dataset.view) {
      state.view = t.dataset.view;
      localStorage.setItem("mock.plan.view", state.view);
      bar.querySelectorAll("[data-view]").forEach((b) => b.setAttribute("aria-pressed", b.dataset.view === state.view));
      rerender();
    } else if ("reset" in t.dataset) {
      location.reload();
    }
  });
  const header = document.createElement("header");
  header.className = "app-header";
  header.innerHTML = `<div class="wrap ${document.body.dataset.wide ? "wide" : ""}">
    <a class="brand" href="#">Mimos</a>
    <nav class="nav"><a href="#">Kitchen</a><a href="#" class="on">Plan</a><a href="#">Recipes</a><a href="#">Log</a><a href="#">Fuel</a><a href="#">Shopping</a></nav>
    <span class="avatar">NO</span></div>`;
  document.body.prepend(bar, header);
}

function rerender() {
  if (window.render) window.render();
}

function pageHeader(extraActions = "") {
  return `<div class="page-header">
    <div><p class="eyebrow label">Week of ${dayLabel(state.days[0].date)}</p><h1>Plan</h1><div class="rule"></div></div>
    <div class="actions">
      <button class="button secondary">← Previous</button><button class="button secondary">This week</button>
      <button class="button secondary">Next →</button><a class="button secondary" href="#">Shopping list</a>${extraActions}
    </div></div>`;
}

document.addEventListener("DOMContentLoaded", () => {
  chrome();
  rerender();
});
