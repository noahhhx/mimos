-- Auth vertical slice: lightweight user profile keyed by the OIDC subject.
-- Created lazily on the first authenticated request; never written by the
-- user in this migration (display name comes from the identity provider).
create table user_profile (
    id uuid primary key default gen_random_uuid(),
    subject_id text not null unique,
    display_name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
