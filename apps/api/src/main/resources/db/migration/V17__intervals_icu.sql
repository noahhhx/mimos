-- A person's intervals.icu connection (ADR-0022): their athlete id and
-- personal API key, sealed with AES-256-GCM under MIMOS_SECRET_KEY, so the
-- key is never stored in plain text. One per person, like the fuel
-- profile, and never household data.
create table intervals_icu_connection (
    profile_id uuid primary key references user_profile (id) on delete cascade,
    athlete_id text not null,
    key_ciphertext bytea not null,
    key_nonce bytea not null,
    status text not null check (status in ('CONNECTED', 'BROKEN')),
    last_sync_at timestamptz,
    last_error text
);
