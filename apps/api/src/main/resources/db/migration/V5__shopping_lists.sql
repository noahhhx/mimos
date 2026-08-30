-- Shopping lists (roadmap step 9): one generated list per profile per week.
-- Items are aggregated (name + unit) across the week's planned recipes and
-- grouped by aisle category; regeneration replaces the items while
-- preserving checked state (matched by name + unit).
create table shopping_list (
    id uuid primary key default gen_random_uuid(),
    owner_profile_id uuid not null references user_profile (id),
    start_date date not null,
    generated_at timestamptz not null,
    unique (owner_profile_id, start_date)
);

create table shopping_list_item (
    id uuid primary key default gen_random_uuid(),
    shopping_list_id uuid not null references shopping_list (id) on delete cascade,
    position integer not null,
    name text not null,
    unit text,
    quantity numeric not null,
    category text not null,
    checked boolean not null default false
);

create index shopping_list_item_list_idx on shopping_list_item (shopping_list_id);
