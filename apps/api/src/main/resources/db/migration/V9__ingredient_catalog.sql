-- Ingredient catalog and calculated nutrition (ADR-0015). The catalog is
-- seeded instance content keyed by slug; recipe lines link to it by slug.
-- Every existing recipe keeps the nutrition its author typed (MANUAL).
create table catalog_ingredient (
    slug text primary key,
    name text not null,
    basis text not null check (basis in ('PER_100_G', 'PER_100_ML', 'PER_PIECE')),
    calories numeric not null check (calories >= 0),
    protein_g numeric not null check (protein_g >= 0),
    carbs_g numeric not null check (carbs_g >= 0),
    fat_g numeric not null check (fat_g >= 0)
);

alter table recipe_ingredient add column catalog_slug text references catalog_ingredient (slug);

alter table recipe
    add column nutrition_source text not null default 'MANUAL'
        check (nutrition_source in ('MANUAL', 'INGREDIENTS'));
