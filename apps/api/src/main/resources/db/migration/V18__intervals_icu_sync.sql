-- Syncing training from intervals.icu (ADR-0022).
--
-- retry_at: when intervals.icu answered a sync with 429, the time its
-- Retry-After names; nothing calls intervals.icu for that person before
-- then. sync_claim and sync_claimed_at: the sync running for the person,
-- if any. A sync claims the row before calling intervals.icu and stores
-- its result only while its claim stands, so two syncs never run at once,
-- and connecting again or disconnecting (which clear or delete the claim)
-- always wins over a sync that started before.
alter table intervals_icu_connection
    add column retry_at timestamptz,
    add column sync_claim uuid,
    add column sync_claimed_at timestamptz;

-- A synced session's id comes from its person, kind, and source id, so a
-- planned workout and an activity that share a source id are two
-- sessions. The unique index now agrees.
drop index training_session_external_idx;
create unique index training_session_external_idx on training_session (profile_id, source, kind, external_id)
    where external_id is not null;
