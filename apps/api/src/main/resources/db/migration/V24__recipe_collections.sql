-- Recipe collections, shown as "tabs" (ADR-0036). A collection is household
-- data: its name, the tags it gathers, and the recipes picked into it. A
-- recipe is a member when it is picked or carries any of the tags; that rule
-- is one query, RecipeCollectionRepository.memberIds. Tags are stored
-- lowercased and match recipe tags case-insensitively.
create table recipe_collection (
    id uuid primary key,
    household_id uuid not null references household (id),
    name text not null,
    created_at timestamptz not null
);

-- Names are unique within a household, ignoring case.
create unique index recipe_collection_household_name_key on recipe_collection (household_id, lower(name));

create table recipe_collection_tag (
    collection_id uuid not null references recipe_collection (id) on delete cascade,
    tag text not null check (tag = lower(tag)),
    primary key (collection_id, tag)
);

-- A deleted recipe drops out of every collection that picked it.
create table recipe_collection_recipe (
    collection_id uuid not null references recipe_collection (id) on delete cascade,
    recipe_id uuid not null references recipe (id) on delete cascade,
    primary key (collection_id, recipe_id)
);
create index recipe_collection_recipe_recipe_idx on recipe_collection_recipe (recipe_id);

-- One cover picture per collection, bytes inline: a single small row per
-- collection, never read by a list. Replacing a cover gives it a new id, so
-- an image URL carrying the id can be cached as immutable.
create table recipe_collection_picture (
    collection_id uuid primary key references recipe_collection (id) on delete cascade,
    id uuid not null unique,
    format text not null check (format in ('JPEG', 'PNG', 'WEBP')),
    bytes bytea not null
);
alter table recipe_collection_picture alter column bytes set storage external;

-- The collection a person opens Recipes on: a viewing preference, so per
-- person, not per household. At most one each. Reads join it to the
-- person's current household, so a default left behind in a household they
-- left is ignored.
create table recipe_collection_default (
    profile_id uuid primary key references user_profile (id) on delete cascade,
    collection_id uuid not null references recipe_collection (id) on delete cascade
);
create index recipe_collection_default_collection_idx on recipe_collection_default (collection_id);

create index recipe_tag_lower_tag_idx on recipe_tag (lower(tag));
