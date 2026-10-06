package io.github.noahhhx.mimos.planning.shopping;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** JDBC persistence for shopping lists and their items. */
@Repository
public class ShoppingListRepository {

    private static final String SELECT_LIST = """
            select id, start_date, generated_at
            from shopping_list
            where owner_profile_id = ? and start_date = ?
            """;

    private static final String SELECT_ITEMS = """
            select i.id, i.name, i.unit, i.quantity, i.category, i.checked
            from shopping_list_item i
            join shopping_list l on l.id = i.shopping_list_id
            where l.owner_profile_id = ? and l.start_date = ?
            order by i.position
            """;

    private final JdbcTemplate jdbc;

    public ShoppingListRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Replaces the owner's list for the week (items cascade). */
    public void replaceList(UUID ownerId, LocalDate startDate, Instant generatedAt, List<ItemRow> items) {
        jdbc.update("delete from shopping_list where owner_profile_id = ? and start_date = ?", ownerId, startDate);
        jdbc.update(
                "insert into shopping_list (owner_profile_id, start_date, generated_at) values (?, ?, ?)",
                ownerId,
                startDate,
                Timestamp.from(generatedAt));
        UUID listId = requireListId(ownerId, startDate);
        int position = 0;
        for (ItemRow item : items) {
            jdbc.update(
                    """
                    insert into shopping_list_item (shopping_list_id, position, name, unit, quantity, category, checked)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    listId,
                    position++,
                    item.name(),
                    item.unit(),
                    item.quantity(),
                    item.category(),
                    item.checked());
        }
    }

    /** The owner's generated list for the week, if any. */
    public Optional<ShoppingList> findList(UUID ownerId, LocalDate startDate) {
        List<UUID> ids = jdbc.queryForList(
                "select id from shopping_list where owner_profile_id = ? and start_date = ?",
                UUID.class,
                ownerId,
                startDate);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        UUID listId = ids.getFirst();
        var generatedAt = requireNonNullTimestamp(
                jdbc.queryForObject("select generated_at from shopping_list where id = ?", Timestamp.class, listId));
        List<ShoppingList.ShoppingListItem> items = jdbc.query(SELECT_ITEMS, ITEM_MAPPER, ownerId, startDate);
        return Optional.of(new ShoppingList(listId, startDate, generatedAt, items));
    }

    /** Every list the owner has, oldest week first. */
    public List<ShoppingList> findAllLists(UUID ownerId) {
        return jdbc
                .queryForList(
                        "select start_date from shopping_list where owner_profile_id = ? order by start_date",
                        LocalDate.class,
                        ownerId)
                .stream()
                .flatMap(startDate -> findList(ownerId, startDate).stream())
                .toList();
    }

    /** Whether any of the owner's lists has an item (an empty generated list holds nothing). */
    public boolean hasItems(UUID ownerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from shopping_list_item i
                    join shopping_list l on l.id = i.shopping_list_id
                    where l.owner_profile_id = ?)
                """, Boolean.class, ownerId));
    }

    /** The item if it belongs to the owner's list for the week. */
    public Optional<ShoppingList.ShoppingListItem> findItem(UUID ownerId, LocalDate startDate, UUID itemId) {
        List<ShoppingList.ShoppingListItem> items = jdbc.query("""
                select i.id, i.name, i.unit, i.quantity, i.category, i.checked
                from shopping_list_item i
                join shopping_list l on l.id = i.shopping_list_id
                where l.owner_profile_id = ? and l.start_date = ? and i.id = ?
                """, ITEM_MAPPER, ownerId, startDate, itemId);
        return items.isEmpty() ? Optional.empty() : Optional.of(items.getFirst());
    }

    public void updateChecked(UUID itemId, boolean checked) {
        jdbc.update("update shopping_list_item set checked = ? where id = ?", checked, itemId);
    }

    private UUID requireListId(UUID ownerId, LocalDate startDate) {
        return java.util.Objects.requireNonNull(
                jdbc.queryForObject(
                        "select id from shopping_list where owner_profile_id = ? and start_date = ?",
                        UUID.class,
                        ownerId,
                        startDate),
                "list row must exist after insert");
    }

    private static Instant requireNonNullTimestamp(@Nullable Timestamp value) {
        return java.util.Objects.requireNonNull(value, "generated_at must not be null")
                .toInstant();
    }

    /** An item to insert (id and position are assigned by the repository). */
    public record ItemRow(
            String name, @Nullable String unit, @Nullable Double quantity, String category, boolean checked) {}

    private static @Nullable Double nullableDouble(ResultSet rs, String column) throws SQLException {
        java.math.BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    private static final RowMapper<ShoppingList.ShoppingListItem> ITEM_MAPPER = new ItemMapper();

    private static final class ItemMapper implements RowMapper<ShoppingList.ShoppingListItem> {

        @Override
        public ShoppingList.ShoppingListItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ShoppingList.ShoppingListItem(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("unit"),
                    nullableDouble(rs, "quantity"),
                    rs.getString("category"),
                    rs.getBoolean("checked"));
        }
    }
}
