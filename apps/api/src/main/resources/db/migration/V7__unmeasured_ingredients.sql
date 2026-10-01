-- Ingredients may be unmeasured ("salt, to taste"), and a shopping-list line
-- made only of unmeasured ingredients has no total (ADR-0007). Existing rows
-- all have a quantity, so relaxing the constraint is safe on existing data.
alter table recipe_ingredient alter column quantity drop not null;
alter table shopping_list_item alter column quantity drop not null;
