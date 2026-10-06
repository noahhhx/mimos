package io.github.noahhhx.mimos.planning.logging;

import io.github.noahhhx.mimos.planning.plan.MealType;
import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** JDBC persistence for meal logs. */
@Repository
public class MealLogRepository {

    private static final String SELECT_BASE = """
            select id, log_date, meal_type, recipe_id, description, servings,
                   calories, protein_g, carbs_g, fat_g, logged_at
            from meal_log
            """;

    private static final String ORDER = """
             order by log_date,
                      case meal_type
                          when 'BREAKFAST' then 0
                          when 'LUNCH' then 1
                          when 'DINNER' then 2
                          when 'SNACK' then 3
                      end,
                      logged_at
            """;

    private final JdbcTemplate jdbc;

    public MealLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID ownerProfileId, MealLog log) {
        jdbc.update(
                """
                insert into meal_log (id, owner_profile_id, log_date, meal_type, recipe_id, description,
                                      servings, calories, protein_g, carbs_g, fat_g, logged_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                log.id(),
                ownerProfileId,
                Date.valueOf(log.date()),
                log.mealType().name(),
                log.recipeId(),
                log.description(),
                log.servings(),
                log.nutrition().calories(),
                log.nutrition().proteinG(),
                log.nutrition().carbsG(),
                log.nutrition().fatG(),
                Timestamp.from(log.loggedAt()));
    }

    /** The owner's logs in a date range (inclusive), in display order. */
    public List<MealLog> findRange(UUID ownerProfileId, LocalDate from, LocalDate to) {
        return jdbc.query(
                SELECT_BASE + " where owner_profile_id = ? and log_date between ? and ?" + ORDER,
                LOG_MAPPER,
                ownerProfileId,
                Date.valueOf(from),
                Date.valueOf(to));
    }

    /** Every log the owner has, in display order. */
    public List<MealLog> findAll(UUID ownerProfileId) {
        return jdbc.query(SELECT_BASE + " where owner_profile_id = ?" + ORDER, LOG_MAPPER, ownerProfileId);
    }

    public boolean existsFor(UUID ownerProfileId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from meal_log where owner_profile_id = ?)", Boolean.class, ownerProfileId));
    }

    /** The owner's log entry, if it exists. */
    public Optional<MealLog> findById(UUID ownerProfileId, UUID logId) {
        List<MealLog> logs =
                jdbc.query(SELECT_BASE + " where owner_profile_id = ? and id = ?", LOG_MAPPER, ownerProfileId, logId);
        return logs.isEmpty() ? Optional.empty() : Optional.of(logs.getFirst());
    }

    /** Clears the owner's links to these recipes; the logs and their nutrition stay. */
    public void unlinkRecipes(UUID ownerProfileId, Collection<UUID> recipeIds) {
        if (recipeIds.isEmpty()) {
            return;
        }
        jdbc.update(
                "update meal_log set recipe_id = null where owner_profile_id = ? and recipe_id in ("
                        + String.join(",", Collections.nCopies(recipeIds.size(), "?")) + ")",
                Stream.concat(Stream.of(ownerProfileId), recipeIds.stream()).toArray());
    }

    public boolean deleteById(UUID ownerProfileId, UUID logId) {
        return jdbc.update("delete from meal_log where owner_profile_id = ? and id = ?", ownerProfileId, logId) > 0;
    }

    private static final RowMapper<MealLog> LOG_MAPPER = new LogMapper();

    private static final class LogMapper implements RowMapper<MealLog> {

        @Override
        public MealLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MealLog(
                    rs.getObject("id", UUID.class),
                    rs.getDate("log_date").toLocalDate(),
                    MealType.valueOf(rs.getString("meal_type")),
                    rs.getObject("recipe_id", UUID.class),
                    rs.getString("description"),
                    rs.getDouble("servings"),
                    new Nutrition(
                            nullableDouble(rs, "calories"),
                            nullableDouble(rs, "protein_g"),
                            nullableDouble(rs, "carbs_g"),
                            nullableDouble(rs, "fat_g")),
                    rs.getTimestamp("logged_at").toInstant());
        }

        private static @Nullable Double nullableDouble(ResultSet rs, String column) throws SQLException {
            double value = rs.getDouble(column);
            return rs.wasNull() ? null : value;
        }
    }
}
