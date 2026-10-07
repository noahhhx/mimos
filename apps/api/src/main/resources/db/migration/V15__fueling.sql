-- Training-aware fuel targets (ADR-0022): each person's fuel profile and
-- training sessions, keyed by profile id like meal logs. Targets are
-- calculated when read, so none are stored. Nothing here is household
-- data, so joining or leaving a household leaves it alone.
create table fuel_profile (
    profile_id uuid primary key references user_profile (id) on delete cascade,
    enabled boolean not null,
    weight_kg numeric not null,
    sex text not null check (sex in ('FEMALE', 'MALE', 'UNSPECIFIED')),
    body_fat_percent numeric,
    ftp_watts integer,
    goal text not null check (goal in ('FUEL', 'GRADUAL_LOSS', 'FASTER_LOSS', 'GAIN')),
    protein_g_per_kg numeric not null
);

-- One row per session, entered by hand or synced. A synced session keeps
-- its source's id, so a sync can replace the person's synced rows in a
-- date range without touching the ones they entered.
create table training_session (
    id uuid primary key,
    profile_id uuid not null references user_profile (id) on delete cascade,
    session_date date not null,
    source text not null check (source in ('MANUAL', 'INTERVALS_ICU')),
    kind text not null check (kind in ('PLANNED', 'COMPLETED')),
    sport text not null check (sport in ('RIDE', 'RUN', 'SWIM', 'STRENGTH', 'OTHER')),
    duration_minutes integer not null check (duration_minutes between 1 and 1440),
    work_kj numeric check (work_kj >= 0),
    intensity text check (intensity in ('EASY', 'ENDURANCE', 'HARD')),
    external_id text,
    -- clock_timestamp, not now: sessions added in one transaction keep the order they were added in.
    created_at timestamptz not null default clock_timestamp(),
    check ((source = 'MANUAL') = (external_id is null))
);

create index training_session_profile_date_idx on training_session (profile_id, session_date);

create unique index training_session_external_idx on training_session (profile_id, source, external_id)
    where external_id is not null;
