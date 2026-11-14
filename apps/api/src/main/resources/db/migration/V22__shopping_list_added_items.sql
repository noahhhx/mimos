-- Items added to a shopping list by hand, not generated from the plan
-- (ADR-0027). Regenerating the list keeps them. Every existing item was
-- generated, so the default is right for existing rows.
alter table shopping_list_item add column added boolean not null default false;
