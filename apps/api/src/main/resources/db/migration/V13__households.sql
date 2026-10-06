-- Households (ADR-0019). Every profile belongs to exactly one household,
-- which owns the shared data: recipes, personal ingredients, plans,
-- shopping lists, and plugin opt-ins and pseudonyms. Meal logs stay per
-- profile. Each existing profile becomes a household of one that owns
-- what the profile owned, and each plan entry gets its plan's owner as its
-- only diner. Plugin pseudonyms keep their UUIDs, so a plugin's stored data
-- about a user stays reachable as data about their household.
create table household (
    id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now()
);

alter table user_profile add column household_id uuid;
update user_profile set household_id = gen_random_uuid();
insert into household (id, created_at) select household_id, created_at from user_profile;
alter table user_profile
    alter column household_id set not null,
    add constraint user_profile_household_id_fkey foreign key (household_id) references household (id);
create index user_profile_household_idx on user_profile (household_id);

-- A null household still marks a curated library recipe.
alter table recipe
    add column household_id uuid references household (id),
    add column created_by_profile_id uuid references user_profile (id) on delete set null;
update recipe
set household_id = p.household_id, created_by_profile_id = p.id
from user_profile p
where p.id = recipe.owner_profile_id;
alter table recipe drop constraint recipe_library_has_slug;
drop index recipe_owner_idx;
alter table recipe drop column owner_profile_id;
alter table recipe add constraint recipe_library_has_slug check (household_id is not null or slug is not null);
create index recipe_household_idx on recipe (household_id);

-- A null household still marks a shared, seeded catalog entry.
alter table catalog_ingredient add column household_id uuid references household (id) on delete cascade;
update catalog_ingredient
set household_id = p.household_id
from user_profile p
where p.id = catalog_ingredient.owner_profile_id;
drop index catalog_ingredient_owner_idx;
alter table catalog_ingredient drop column owner_profile_id;
create index catalog_ingredient_household_idx on catalog_ingredient (household_id);

create table meal_plan_entry_diner (
    entry_id uuid not null references meal_plan_entry (id) on delete cascade,
    profile_id uuid not null references user_profile (id),
    primary key (entry_id, profile_id)
);
create index meal_plan_entry_diner_profile_idx on meal_plan_entry_diner (profile_id);
insert into meal_plan_entry_diner (entry_id, profile_id)
select e.id, p.owner_profile_id
from meal_plan_entry e
join meal_plan p on p.id = e.meal_plan_id;

alter table meal_plan add column household_id uuid references household (id);
update meal_plan
set household_id = p.household_id
from user_profile p
where p.id = meal_plan.owner_profile_id;
alter table meal_plan drop column owner_profile_id;
alter table meal_plan
    alter column household_id set not null,
    add constraint meal_plan_household_id_start_date_key unique (household_id, start_date);

alter table shopping_list add column household_id uuid references household (id);
update shopping_list
set household_id = p.household_id
from user_profile p
where p.id = shopping_list.owner_profile_id;
alter table shopping_list drop column owner_profile_id;
alter table shopping_list
    alter column household_id set not null,
    add constraint shopping_list_household_id_start_date_key unique (household_id, start_date);

alter table plugin_opt_in add column household_id uuid references household (id);
update plugin_opt_in
set household_id = p.household_id
from user_profile p
where p.id = plugin_opt_in.profile_id;
alter table plugin_opt_in drop column profile_id;
alter table plugin_opt_in
    alter column household_id set not null,
    add primary key (household_id, plugin_id);

alter table plugin_subject add column household_id uuid references household (id);
update plugin_subject
set household_id = p.household_id
from user_profile p
where p.id = plugin_subject.profile_id;
alter table plugin_subject drop column profile_id;
alter table plugin_subject
    alter column household_id set not null,
    add primary key (household_id, plugin_id);
