package io.github.noahhhx.mimos.fueling;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FuelRepository {

    private static final String SELECT_SESSION = """
            select id, profile_id, session_date, source, kind, sport, duration_minutes,
                   work_kj, intensity, external_id
            from training_session
            """;

    private static final String ORDER = " order by session_date, created_at, id";

    private final JdbcTemplate jdbc;

    public FuelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<FuelProfile> findProfile(UUID profileId) {
        return jdbc.query("""
                        select profile_id, enabled, weight_kg, sex, body_fat_percent, ftp_watts, goal, protein_g_per_kg
                        from fuel_profile where profile_id = ?
                        """, PROFILE_MAPPER, profileId).stream().findFirst();
    }

    public void saveProfile(FuelProfile profile) {
        jdbc.update(
                """
                insert into fuel_profile (profile_id, enabled, weight_kg, sex, body_fat_percent, ftp_watts, goal,
                                          protein_g_per_kg)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (profile_id) do update
                set enabled = excluded.enabled, weight_kg = excluded.weight_kg, sex = excluded.sex,
                    body_fat_percent = excluded.body_fat_percent, ftp_watts = excluded.ftp_watts,
                    goal = excluded.goal, protein_g_per_kg = excluded.protein_g_per_kg
                """,
                profile.profileId(),
                profile.enabled(),
                profile.weightKg(),
                profile.sex().name(),
                profile.bodyFatPercent(),
                profile.ftpWatts(),
                profile.goal().name(),
                profile.proteinGPerKg());
    }

    public boolean hasData(UUID profileId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from fuel_profile where profile_id = ?)
                    or exists (select 1 from training_session where profile_id = ?)
                """, Boolean.class, profileId, profileId));
    }

    public void insertSession(TrainingSession session) {
        jdbc.update(
                """
                insert into training_session (id, profile_id, session_date, source, kind, sport, duration_minutes,
                                              work_kj, intensity, external_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                session.id(),
                session.profileId(),
                Date.valueOf(session.date()),
                session.source().name(),
                session.kind().name(),
                session.sport().name(),
                session.durationMinutes(),
                session.workKj(),
                session.intensity() == null ? null : session.intensity().name(),
                session.externalId());
    }

    public List<TrainingSession> findSessions(UUID profileId, LocalDate from, LocalDate to) {
        return jdbc.query(
                SELECT_SESSION + " where profile_id = ? and session_date between ? and ?" + ORDER,
                SESSION_MAPPER,
                profileId,
                Date.valueOf(from),
                Date.valueOf(to));
    }

    public List<TrainingSession> findManual(UUID profileId) {
        return jdbc.query(
                SELECT_SESSION + " where profile_id = ? and source = 'MANUAL'" + ORDER, SESSION_MAPPER, profileId);
    }

    public Optional<TrainingSession> findSession(UUID profileId, UUID sessionId) {
        return jdbc
                .query(SELECT_SESSION + " where profile_id = ? and id = ?", SESSION_MAPPER, profileId, sessionId)
                .stream()
                .findFirst();
    }

    public void deleteSession(UUID profileId, UUID sessionId) {
        jdbc.update("delete from training_session where profile_id = ? and id = ?", profileId, sessionId);
    }

    public void deleteSynced(UUID profileId) {
        jdbc.update("delete from training_session where profile_id = ? and source <> 'MANUAL'", profileId);
    }

    /** Deletes the source's sessions dated in the range, and any of {@code ids} wherever they are dated. */
    public void deleteSynced(UUID profileId, Source source, LocalDate from, LocalDate to, List<UUID> ids) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    delete from training_session
                    where profile_id = ? and source = ? and (session_date between ? and ? or id = any(?))
                    """);
            statement.setObject(1, profileId);
            statement.setString(2, source.name());
            statement.setDate(3, Date.valueOf(from));
            statement.setDate(4, Date.valueOf(to));
            statement.setArray(5, connection.createArrayOf("uuid", ids.toArray()));
            return statement;
        });
    }

    private static final RowMapper<FuelProfile> PROFILE_MAPPER = (rs, rowNum) -> new FuelProfile(
            rs.getObject("profile_id", UUID.class),
            rs.getBoolean("enabled"),
            rs.getDouble("weight_kg"),
            Sex.valueOf(rs.getString("sex")),
            nullableDouble(rs, "body_fat_percent"),
            rs.getObject("ftp_watts", Integer.class),
            Goal.valueOf(rs.getString("goal")),
            rs.getDouble("protein_g_per_kg"));

    private static final RowMapper<TrainingSession> SESSION_MAPPER = (rs, rowNum) -> {
        String intensity = rs.getString("intensity");
        return new TrainingSession(
                rs.getObject("id", UUID.class),
                rs.getObject("profile_id", UUID.class),
                rs.getDate("session_date").toLocalDate(),
                Source.valueOf(rs.getString("source")),
                Kind.valueOf(rs.getString("kind")),
                Sport.valueOf(rs.getString("sport")),
                rs.getInt("duration_minutes"),
                nullableDouble(rs, "work_kj"),
                intensity == null ? null : Intensity.valueOf(intensity),
                rs.getString("external_id"));
    };

    private static @Nullable Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
