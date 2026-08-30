-- Meal planning (roadmap step 8): a plan per profile per week (keyed by its
-- Monday) and its entries. Entries reference recipes across the module
-- boundary via FK for integrity (deleting a recipe removes its planned
-- meals); core-planning reads recipe data only through core-recipes' public
-- interface (ADR-0001). The week range (entry_date within the plan's
-- Monday..Sunday) is enforced by the domain service.
create table meal_plan (
    id uuid primary key default gen_random_uuid(),
    owner_profile_id uuid not null references user_profile (id),
    start_date date not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (owner_profile_id, start_date)
);

create table meal_plan_entry (
    id uuid primary key default gen_random_uuid(),
    meal_plan_id uuid not null references meal_plan (id) on delete cascade,
    entry_date date not null,
    meal_type text not null check (meal_type in ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    recipe_id uuid not null references recipe (id) on delete cascade,
    servings numeric not null default 1,
    constraint meal_plan_entry_servings_positive check (servings > 0)
);

create index meal_plan_entry_plan_idx on meal_plan_entry (meal_plan_id);
