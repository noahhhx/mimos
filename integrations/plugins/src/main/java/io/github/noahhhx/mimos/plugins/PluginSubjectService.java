package io.github.noahhhx.mimos.plugins;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The pseudonym each plugin knows a household by (ADR-0017, ADR-0019). A
 * subject is a random UUID, created the first time core calls a plugin for
 * a household and stored in {@code plugin_subject}. It is unrelated to the
 * Keycloak subject and the household and profile ids, and differs per
 * plugin, so plugins cannot join their data on it. Turning a plugin off
 * leaves the row, so a plugin's memory of the household survives turning
 * it back on.
 */
@Service
public class PluginSubjectService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PluginSubjectService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** This household's subject for this plugin, created on first use. Concurrent first calls agree on one. */
    public UUID subjectFor(UUID householdId, String pluginId) {
        UUID existing = find(householdId, pluginId);
        if (existing != null) {
            return existing;
        }
        jdbc.update("""
                insert into plugin_subject (household_id, plugin_id, subject, created_at)
                values (?, ?, ?, ?)
                on conflict (household_id, plugin_id) do nothing
                """, householdId, pluginId, UUID.randomUUID(), Timestamp.from(clock.instant()));
        UUID created = find(householdId, pluginId);
        if (created == null) {
            throw new IllegalStateException("plugin subject for " + pluginId + " was not stored");
        }
        return created;
    }

    /**
     * Forgets this household's subjects, for a household that is going away
     * (ADR-0019). What a plugin stored against them can no longer be
     * reached through Mimos.
     */
    public void deleteAll(UUID householdId) {
        jdbc.update("delete from plugin_subject where household_id = ?", householdId);
    }

    private @Nullable UUID find(UUID householdId, String pluginId) {
        List<UUID> subjects = jdbc.queryForList(
                "select subject from plugin_subject where household_id = ? and plugin_id = ?",
                UUID.class,
                householdId,
                pluginId);
        return subjects.isEmpty() ? null : subjects.get(0);
    }
}
