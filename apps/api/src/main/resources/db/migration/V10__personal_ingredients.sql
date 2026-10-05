-- Personal ingredients and line notes (ADR-0016). A catalog entry with an
-- owner is that user's own, visible only to them; the seeded entries stay
-- shared (no owner). Deleting an entry unlinks the lines that used it, and
-- a line's prep note ("minced", "to serve") moves out of its name.
alter table catalog_ingredient add column owner_profile_id uuid references user_profile (id) on delete cascade;
create index catalog_ingredient_owner_idx on catalog_ingredient (owner_profile_id);

alter table recipe_ingredient drop constraint recipe_ingredient_catalog_slug_fkey;
alter table recipe_ingredient
    add constraint recipe_ingredient_catalog_slug_fkey
        foreign key (catalog_slug) references catalog_ingredient (slug) on delete set null;

alter table recipe_ingredient add column note text;
