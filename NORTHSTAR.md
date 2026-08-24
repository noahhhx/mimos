# Mimos — North Star

The product vision. Non-technical by design — if you're looking for how we build
this, read `AGENTS.md` instead.

## What Mimos is

Mimos is a recipe companion for people who cook. It comes with a library of
free recipes, lets you write and keep your own, and turns "what am I cooking
this week?" into plans, shopping lists, and a picture of what you're actually
eating.

It is built for two kinds of homes at once: the person who signs up and uses it
like any website, and the person who runs it themselves on their own hardware.
Both are first-class. Mimos is never "the cloud version, plus a charity mode."

## Who it's for

- **Everyday cooks** who want their recipes in one place and a shopping list
  that builds itself from the week's plan.
- **Health-conscious eaters** who want to see what a meal plan adds up to —
  calories and macros — without turning dinner into homework.
- **Athletes** (later) whose training should feed into what they eat, not sit
  in a separate app they never open.
- **Tinkerers** who want to extend their own kitchen software with plugins.

## What it does

### Core (from day one)

1. **Recipe library** — a curated set of free recipes, searchable and
   browsable, with everything needed to actually cook: ingredients, steps,
   servings, and per-serving nutrition.
2. **Personal recipes** — anyone can create, edit, and keep their own recipes
   with the same richness as the library. Yours stay yours.
3. **Meal planning** — plan meals for the week ahead, drawing from both the
   library and personal recipes.
4. **Shopping lists** — a plan becomes a shopping list. Items are grouped
   sensibly, quantities are added up across recipes, and you can tick things
   off in the store.
5. **Calorie and macro logging** — meals planned are meals logged. Tracking
   flows naturally out of planning instead of being a separate chore; you can
   also log ad-hoc meals.

### Next

- **Training-aware suggestions** — connect your training (via intervals.icu)
  and Mimos suggests meal plans that fit your workload: more food on the big
  days, hitting calorie and macro targets across the week rather than
  punishing any single day.

### Long term

- **A plugin ecosystem** — Mimos is extensible by its users. Plugins can add
  features the core team never thought of. The first plugin already has a
  customer: one that suggests a country to theme your weekly meal plan
  around. If a plugin like that can be built without touching the core,
  the architecture is right.

## Product principles

1. **Yours, anywhere.** Every feature works the same whether Mimos is running
   in our cloud or on a laptop in someone's kitchen. A feature that only
   works in the cloud is a feature we don't ship.
2. **Cooking first.** Screens are readable with flour on your hands. Recipes,
   plans, and lists are the primary objects; everything else serves them.
3. **Tracking without moralising.** Calories and macros are information, not
   judgement. No scolding, no streaks-as-pressure, no dark patterns.
4. **Free core, open door.** The recipe library and personal cooking tools are
   free. There is no paywall between people and their own recipes.
5. **Extensible by design.** Plugins are a product surface, not an afterthought.
   We judge architectural choices by what they let third parties build.
6. **Boring where it counts.** Users trust us with their data and their
   dinner. We'd rather be dependable than novel.

## Non-goals (for now)

- Native mobile apps — later, once the web product has proven its shape.
- Social features (feeds, follows, comments). Mimos is personal; sharing a
   recipe is as social as it needs to get.
- Being a recipe publisher. We curate a starting library; the community's
  recipes live with their owners, not with us.

## What success looks like

- Someone plans Sunday's meals in under five minutes and shops with a list
  that's actually complete.
- A self-hoster recommends Mimos to a friend without adding "but".
- The plugin directory has things on it we didn't build.
- Training data made someone's race-week dinner better.
