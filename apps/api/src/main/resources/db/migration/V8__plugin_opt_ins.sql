-- Per-user plugin opt-in (ADR-0013): a row means the user turned the
-- plugin on; no row means off, which is where every plugin starts. Plugin
-- ids come from instance configuration, not a table, so they carry no
-- foreign key; a row may outlive its plugin's registration.
create table plugin_opt_in (
    profile_id uuid not null references user_profile (id),
    plugin_id text not null,
    enabled_at timestamptz not null default now(),
    primary key (profile_id, plugin_id)
);
