package io.github.noahhhx.mimos.planning.plan;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC persistence for meal plans and their entries. Recipe titles are not
 * stored here — core-planning resolves them through core-recipes'
 * public interface (ADR-0001) — so entries keep only the recipe id.
 */
@Repository
public class MealPlanRepository {

    private static final String SELECT_ENTRIES = """
            select e.id, e.entry_date, e.meal_type, e.recipe_id, e.servings
            from meal_plan_entry e
            join meal_plan p on p.id = e.meal_plan_id
            where p.owner_profile_id = ? and p.start_date = ?
            order by e.entry_date,
                     case e.meal_type
                         when 'BREAKFAST' then 0
                         when 'LUNCH' then 1
                         when 'DINNER' then 2
                         when 'SNACK' then 3
                     end,
                     e.id
            """;

    private final JdbcTemplate jdbc;

    public MealPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the plan id for the owner's week, creating the (empty) plan if absent. */
    public UUID ensurePlan(UUID ownerProfileId, LocalDate startDate) {
        jdbc.update("""
                insert into meal_plan (owner_profile_id, start_date)
                values (?, ?)
                on conflict (owner_profile_id, start_date) do nothing
                """, ownerProfileId, Date.valueOf(startDate));
        return java.util.Objects.requireNonNull(
                jdbc.queryForObject(
                        "select id from meal_plan where owner_profile_id = ? and start_date = ?",
                        UUID.class,
                        ownerProfileId,
                        Date.valueOf(startDate)),
                "plan row must exist after ensure");
    }

    public void insertEntry(UUID planId, PlannedMealInput entry) {
        jdbc.update(
                """
                insert into meal_plan_entry (id, meal_plan_id, entry_date, meal_type, recipe_id, servings)
                values (?, ?, ?, ?, ?, ?)
                """,
                entry.id(),
                planId,
                Date.valueOf(entry.date()),
                entry.mealType().name(),
                entry.recipeId(),
                entry.servings());
    }

    public void updateServings(UUID entryId, double servings) {
        jdbc.update("update meal_plan_entry set servings = ? where id = ?", servings, entryId);
    }

    public void deleteEntry(UUID entryId) {
        jdbc.update("delete from meal_plan_entry where id = ?", entryId);
    }

    /** The week's entries in display order; titles resolved against the given recipe titles. */
    public List<PlannedMeal> loadEntries(UUID ownerProfileId, LocalDate startDate, Map<UUID, String> recipeTitles) {
        return jdbc
                .query(
                        SELECT_ENTRIES,
                        (rs, i) -> new PlannedMeal(
                                rs.getObject("id", UUID.class),
                                rs.getDate("entry_date").toLocalDate(),
                                MealType.valueOf(rs.getString("meal_type")),
                                rs.getObject("recipe_id", UUID.class),
                                recipeTitles.getOrDefault(rs.getObject("recipe_id", UUID.class), "Deleted recipe"),
                                rs.getDouble("servings")),
                        ownerProfileId,
                        Date.valueOf(startDate))
                .stream()
                .toList();
    }

    /** The week's raw entries (used by the shopping list and logging services). */
    public List<PlannedMealInput> loadEntryInputs(UUID ownerProfileId, LocalDate startDate) {
        return jdbc.query(SELECT_ENTRIES, ENTRY_INPUT_MAPPER, ownerProfileId, Date.valueOf(startDate)).stream()
                .toList();
    }

    /** Every entry the owner has planned, with its week, in week and display order. */
    public List<WeekEntry> loadAllEntryInputs(UUID ownerProfileId) {
        return jdbc.query(
                """
                select p.id as plan_id, p.start_date, e.id, e.entry_date, e.meal_type, e.recipe_id, e.servings
                from meal_plan_entry e
                join meal_plan p on p.id = e.meal_plan_id
                where p.owner_profile_id = ?
                order by p.start_date,
                         e.entry_date,
                         case e.meal_type
                             when 'BREAKFAST' then 0
                             when 'LUNCH' then 1
                             when 'DINNER' then 2
                             when 'SNACK' then 3
                         end,
                         e.id
                """,
                (rs, i) -> new WeekEntry(
                        rs.getObject("plan_id", UUID.class),
                        rs.getDate("start_date").toLocalDate(),
                        ENTRY_INPUT_MAPPER.mapRow(rs, i)),
                ownerProfileId);
    }

    public boolean hasEntries(UUID ownerProfileId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from meal_plan_entry e
                    join meal_plan p on p.id = e.meal_plan_id
                    where p.owner_profile_id = ?)
                """, Boolean.class, ownerProfileId));
    }

    /** A single entry (owner-checked via the plan join), for patch/delete confirmation. */
    public Optional<PlannedMealInput> findEntry(UUID ownerProfileId, UUID entryId) {
        List<PlannedMealInput> rows = jdbc.query("""
                select e.id, e.entry_date, e.meal_type, e.recipe_id, e.servings
                from meal_plan_entry e
                join meal_plan p on p.id = e.meal_plan_id
                where p.owner_profile_id = ? and e.id = ?
                """, ENTRY_INPUT_MAPPER, ownerProfileId, entryId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /** Raw entry data before title resolution. */
    public record PlannedMealInput(UUID id, LocalDate date, MealType mealType, UUID recipeId, double servings) {}

    /** An entry with its plan and the Monday of the plan's week. */
    public record WeekEntry(UUID planId, LocalDate startDate, PlannedMealInput entry) {}

    private static final RowMapper<PlannedMealInput> ENTRY_INPUT_MAPPER = new EntryInputMapper();

    private static final class EntryInputMapper implements RowMapper<PlannedMealInput> {

        @Override
        public PlannedMealInput mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PlannedMealInput(
                    rs.getObject("id", UUID.class),
                    rs.getDate("entry_date").toLocalDate(),
                    MealType.valueOf(rs.getString("meal_type")),
                    rs.getObject("recipe_id", UUID.class),
                    rs.getDouble("servings"));
        }
    }
}
