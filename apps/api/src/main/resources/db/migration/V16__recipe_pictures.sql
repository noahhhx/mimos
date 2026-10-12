-- Recipe pictures (ADR-0023). recipe_picture is the one record that a picture
-- exists; its bytes are in recipe_picture_data, which only the Postgres
-- PictureStore reads and writes, so an S3-compatible store can replace that
-- table later. Keyed by recipe alone: pictures go wherever their recipe goes
-- (a household join moves recipes) and are deleted with it. The size and
-- count limits live in RecipePictureService, not here.
create table recipe_picture (
    id uuid primary key,
    recipe_id uuid not null references recipe (id) on delete cascade,
    position integer not null check (position >= 0),
    format text not null check (format in ('JPEG', 'PNG', 'WEBP')),
    created_at timestamptz not null,
    unique (recipe_id, position)
);

create table recipe_picture_data (
    picture_id uuid primary key references recipe_picture (id) on delete cascade,
    bytes bytea not null
);

-- JPEG, PNG and WebP are compressed already: store them out of line without
-- trying to compress them again.
alter table recipe_picture_data alter column bytes set storage external;
