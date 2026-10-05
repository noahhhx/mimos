-- The pseudonym core gives each plugin for each user (ADR-0017): a random
-- UUID, created the first time core calls that plugin for that user and
-- never changed, so a plugin can keep its own data about the user without
-- learning who they are. It is unrelated to the Keycloak subject and the
-- profile id, and differs per plugin. Turning a plugin off keeps the row,
-- so the plugin's memory survives turning it back on.
create table plugin_subject (
    profile_id uuid not null references user_profile (id),
    plugin_id text not null,
    subject uuid not null unique,
    created_at timestamptz not null,
    primary key (profile_id, plugin_id)
);
