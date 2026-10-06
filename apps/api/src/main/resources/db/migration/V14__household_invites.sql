-- Household invites (ADR-0019): a single-use link to join a household,
-- valid for seven days. Only a SHA-256 hash of the token is stored, so
-- reading this table does not let anyone join a household.
create table household_invite (
    token_hash text primary key,
    household_id uuid not null references household (id) on delete cascade,
    created_by_profile_id uuid references user_profile (id) on delete set null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at timestamptz
);
create index household_invite_household_idx on household_invite (household_id);
