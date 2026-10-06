package io.github.noahhhx.mimos.planning.plan;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
            where p.household_id = ? and p.start_date = ?
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
    public UUID ensurePlan(UUID ownerId, LocalDate startDate) {
        jdbc.update("""
                insert into meal_plan (household_id, start_date)
                values (?, ?)
                on conflict (household_id, start_date) do nothing
                """, ownerId, Date.valueOf(startDate));
        return java.util.Objects.requireNonNull(
                jdbc.queryForObject(
                        "select id from meal_plan where household_id = ? and start_date = ?",
                        UUID.class,
                        ownerId,
                        Date.valueOf(startDate)),
                "plan row must exist after ensure");
    }

    /** Inserts an entry and its diners. */
    public void insertEntry(UUID planId, PlannedMealInput entry, Set<UUID> dinerProfileIds) {
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
        jdbc.batchUpdate(
                "insert into meal_plan_entry_diner (entry_id, profile_id) values (?, ?)",
                dinerProfileIds.stream()
                        .map(profileId -> new Object[] {entry.id(), profileId})
                        .toList());
    }

    public void updateServings(UUID entryId, double servings) {
        jdbc.update("update meal_plan_entry set servings = ? where id = ?", servings, entryId);
    }

    public void deleteEntry(UUID entryId) {
        jdbc.update("delete from meal_plan_entry where id = ?", entryId);
    }

    /** The week's raw entries (used by the shopping list and logging services). */
    public List<PlannedMealInput> loadEntryInputs(UUID ownerId, LocalDate startDate) {
        return jdbc.query(SELECT_ENTRIES, ENTRY_INPUT_MAPPER, ownerId, Date.valueOf(startDate)).stream()
                .toList();
    }

    /** Every entry the owner has planned, with its week, in week and display order. */
    public List<WeekEntry> loadAllEntryInputs(UUID ownerId) {
        return jdbc.query(
                """
                select p.id as plan_id, p.start_date, e.id, e.entry_date, e.meal_type, e.recipe_id, e.servings
                from meal_plan_entry e
                join meal_plan p on p.id = e.meal_plan_id
                where p.household_id = ?
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
                ownerId);
    }

    public boolean hasEntries(UUID ownerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from meal_plan_entry e
                    join meal_plan p on p.id = e.meal_plan_id
                    where p.household_id = ?)
                """, Boolean.class, ownerId));
    }

    /** Deletes every plan the owner has; entries and their diners go with them. */
    public void deleteAllPlans(UUID ownerId) {
        jdbc.update("delete from meal_plan where household_id = ?", ownerId);
    }

    /** Takes the profile off every entry the owner has planned, then deletes the entries nobody eats. */
    public void removeDiner(UUID ownerId, UUID dinerProfileId) {
        jdbc.update("""
                delete from meal_plan_entry_diner d
                using meal_plan_entry e, meal_plan p
                where d.entry_id = e.id and e.meal_plan_id = p.id
                  and p.household_id = ? and d.profile_id = ?
                """, ownerId, dinerProfileId);
        jdbc.update("""
                delete from meal_plan_entry e
                using meal_plan p
                where e.meal_plan_id = p.id and p.household_id = ?
                  and not exists (select 1 from meal_plan_entry_diner d where d.entry_id = e.id)
                """, ownerId);
    }

    /** A single entry (owner-checked via the plan join), for patch/delete confirmation. */
    public Optional<PlannedMealInput> findEntry(UUID ownerId, UUID entryId) {
        List<PlannedMealInput> rows = jdbc.query("""
                select e.id, e.entry_date, e.meal_type, e.recipe_id, e.servings
                from meal_plan_entry e
                join meal_plan p on p.id = e.meal_plan_id
                where p.household_id = ? and e.id = ?
                """, ENTRY_INPUT_MAPPER, ownerId, entryId);
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
