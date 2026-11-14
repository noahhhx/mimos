-- Planned foods (ADR-0026): a plan entry plans exactly one of a recipe and
-- a single food, which is an ingredient by slug (shared or the household's
-- own). recipe_id becomes nullable and food_slug references the catalog
-- with the same cascade a recipe has (V4): deleting an ingredient deletes
-- the entries that plan it. The servings column keeps its name and holds
-- the amount for both: servings of the recipe, or the food's quantity in
-- food_unit (pieces without one).
alter table meal_plan_entry alter column recipe_id drop not null;

alter table meal_plan_entry
    add column food_slug text references catalog_ingredient (slug) on delete cascade,
    add column food_unit text,
    add constraint meal_plan_entry_recipe_xor_food check ((recipe_id is null) <> (food_slug is null)),
    add constraint meal_plan_entry_food_unit check (food_unit is null or food_slug is not null);

create index meal_plan_entry_food_slug_idx on meal_plan_entry (food_slug);
