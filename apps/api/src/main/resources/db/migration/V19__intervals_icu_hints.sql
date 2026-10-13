-- What intervals.icu knows that a fuel profile asks for (ADR-0022), kept
-- from the latest sync that had each value. Synced body fat feeds the
-- targets when the profile has none; the rest are suggestions the person
-- may accept on the Fuel page.
alter table intervals_icu_connection
    add column synced_weight_kg numeric,
    add column synced_body_fat_percent numeric,
    add column synced_sex text check (synced_sex in ('FEMALE', 'MALE')),
    add column synced_ftp_watts integer;

-- The calories intervals.icu reported for an activity: the target's fourth
-- choice for its energy. Synced sessions only, so the export, which holds
-- hand-entered sessions, does not change.
alter table training_session
    add column reported_kcal integer check (reported_kcal between 0 and 20000),
    add check (source <> 'MANUAL' or reported_kcal is null);
