package io.github.noahhhx.mimos.api.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures and reads user profiles. Profiles are created lazily: the first
 * authenticated request for a subject inserts the row with the display
 * name the identity provider knows, together with the household of one it
 * belongs to (ADR-0019), then returns it.
 */
@Service
public class IdentityService {

    private static final String SELECT_PROFILE = """
            select id, subject_id, display_name, household_id, created_at, updated_at
            from user_profile
            where subject_id = ?
            """;

    private static final RowMapper<UserProfileRecord> PROFILE_MAPPER = new ProfileRowMapper();

    private final JdbcTemplate jdbc;

    public IdentityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns the profile for the subject, creating it and its household on
     * first sight. Concurrent first requests agree on one profile and leave
     * no household behind but its own.
     */
    @Transactional
    public UserProfileRecord ensureProfile(String subjectId, String displayName) {
        List<UserProfileRecord> existing = jdbc.query(SELECT_PROFILE, PROFILE_MAPPER, subjectId);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        UUID householdId = Objects.requireNonNull(
                jdbc.queryForObject("insert into household default values returning id", UUID.class));
        int inserted = jdbc.update("""
                insert into user_profile (subject_id, display_name, household_id)
                values (?, ?, ?)
                on conflict (subject_id) do nothing
                """, subjectId, displayName, householdId);
        if (inserted == 0) {
            jdbc.update("delete from household where id = ?", householdId);
        }
        return Objects.requireNonNull(jdbc.queryForObject(SELECT_PROFILE, PROFILE_MAPPER, subjectId));
    }

    /** The display names of these profiles; unknown ids are omitted. */
    public Map<UUID, String> displayNames(Collection<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        jdbc.query(
                "select id, display_name from user_profile where id in ("
                        + String.join(",", Collections.nCopies(profileIds.size(), "?")) + ")",
                (RowCallbackHandler) rs -> names.put(rs.getObject("id", UUID.class), rs.getString("display_name")),
                profileIds.toArray());
        return names;
    }

    /**
     * Locks the household row until the surrounding transaction ends, so
     * household-wide operations (import, ADR-0011) run one at a time.
     */
    public void lockHousehold(UUID householdId) {
        jdbc.queryForList("select id from household where id = ? for update", UUID.class, householdId);
    }

    private static final class ProfileRowMapper implements RowMapper<UserProfileRecord> {

        @Override
        public UserProfileRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserProfileRecord(
                    rs.getObject("id", UUID.class),
                    rs.getString("subject_id"),
                    rs.getString("display_name"),
                    rs.getObject("household_id", UUID.class),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant());
        }
    }
}
