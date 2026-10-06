-- Recipes as ingredients (ADR-0018). An ingredient line may link one of
-- its owner's own recipes instead of a catalog entry, never both. Deleting
-- the linked recipe leaves the line, unlinked, as deleting a personal
-- ingredient does (ADR-0016).
alter table recipe_ingredient
    add column linked_recipe_id uuid references recipe (id) on delete set null,
    add constraint recipe_ingredient_one_link check (catalog_slug is null or linked_recipe_id is null);

create index recipe_ingredient_linked_recipe_idx on recipe_ingredient (linked_recipe_id);
