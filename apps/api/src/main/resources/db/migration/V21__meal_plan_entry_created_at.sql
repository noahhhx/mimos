-- Entries in one slot keep the order they were planned in. Rows that exist
-- already share the migration's moment and fall back to id order, as before.
alter table meal_plan_entry add column created_at timestamptz not null default clock_timestamp();
