-- Recipes (roadmap step 7): recipe, ingredient, step, and tag tables with
-- per-serving nutrition. A null owner_profile_id marks a curated library
-- recipe (owned by the instance, seeded read-only, publicly readable via
-- its slug); a non-null one marks a personal recipe.
create table recipe (
    id uuid primary key default gen_random_uuid(),
    owner_profile_id uuid references user_profile (id),
    slug text unique,
    title text not null,
    description text not null default '',
    servings integer not null,
    prep_minutes integer,
    cook_minutes integer,
    calories numeric,
    protein_g numeric,
    carbs_g numeric,
    fat_g numeric,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint recipe_library_has_slug check (owner_profile_id is not null or slug is not null),
    constraint recipe_servings_positive check (servings > 0)
);

create table recipe_ingredient (
    id uuid primary key default gen_random_uuid(),
    recipe_id uuid not null references recipe (id) on delete cascade,
    position integer not null,
    quantity numeric not null,
    unit text,
    name text not null
);

create table recipe_step (
    id uuid primary key default gen_random_uuid(),
    recipe_id uuid not null references recipe (id) on delete cascade,
    position integer not null,
    instruction text not null
);

create table recipe_tag (
    recipe_id uuid not null references recipe (id) on delete cascade,
    tag text not null,
    primary key (recipe_id, tag)
);

create index recipe_owner_idx on recipe (owner_profile_id);
create index recipe_ingredient_recipe_idx on recipe_ingredient (recipe_id);
create index recipe_step_recipe_idx on recipe_step (recipe_id);
