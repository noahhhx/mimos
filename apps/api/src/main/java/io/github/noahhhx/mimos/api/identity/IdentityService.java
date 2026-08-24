package io.github.noahhhx.mimos.api.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Ensures and reads user profiles. Profiles are created lazily: the first
 * authenticated request for a subject inserts the row (idempotently) with
 * the display name the identity provider knows, then returns it.
 */
@Service
public class IdentityService {

    private static final String SELECT_PROFILE = """
            select id, subject_id, display_name, created_at, updated_at
            from user_profile
            where subject_id = ?
            """;

    private static final RowMapper<UserProfile> PROFILE_MAPPER = new ProfileRowMapper();

    private final JdbcTemplate jdbc;

    public IdentityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the profile for the subject, creating it on first sight. */
    public UserProfile ensureProfile(String subjectId, String displayName) {
        jdbc.update("""
                insert into user_profile (subject_id, display_name)
                values (?, ?)
                on conflict (subject_id) do nothing
                """, subjectId, displayName);
        return jdbc.queryForObject(SELECT_PROFILE, PROFILE_MAPPER, subjectId);
    }

    private static final class ProfileRowMapper implements RowMapper<UserProfile> {

        @Override
        public UserProfile mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserProfile(
                    rs.getObject("id", UUID.class),
                    rs.getString("subject_id"),
                    rs.getString("display_name"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant());
        }
    }
}
