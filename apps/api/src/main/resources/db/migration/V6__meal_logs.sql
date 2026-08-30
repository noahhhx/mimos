-- Calorie and macro logging (roadmap step 10): one row per logged meal.
-- Recipe references are historical: deleting a recipe keeps the log but
-- clears the link (nutrition was copied at logging time).
create table meal_log (
    id uuid primary key default gen_random_uuid(),
    owner_profile_id uuid not null references user_profile (id),
    log_date date not null,
    meal_type text not null check (meal_type in ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    recipe_id uuid references recipe (id) on delete set null,
    description text not null,
    servings numeric not null default 1,
    calories numeric not null default 0,
    protein_g numeric not null default 0,
    carbs_g numeric not null default 0,
    fat_g numeric not null default 0,
    logged_at timestamptz not null default now()
);

create index meal_log_owner_date_idx on meal_log (owner_profile_id, log_date);
