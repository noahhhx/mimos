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

    private static final String SELECT_ITEMS = """
            select i.id, i.name, i.unit, i.quantity, i.category, i.checked, i.added
            from shopping_list_item i
            join shopping_list l on l.id = i.shopping_list_id
            where l.household_id = ? and l.start_date = ?
            order by i.position
            """;

    private final JdbcTemplate jdbc;

    public ShoppingListRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The id of the owner's list for the week, creating an empty one (at
     * {@code generatedAt}) when there is none, locked until the transaction
     * ends. Generating, restoring, and adding to a list all take this lock
     * first, so none of them loses another's items.
     */
    public UUID lockOrCreateList(UUID ownerId, LocalDate startDate, Instant generatedAt) {
        jdbc.update("""
                insert into shopping_list (household_id, start_date, generated_at) values (?, ?, ?)
                on conflict (household_id, start_date) do nothing
                """, ownerId, startDate, Timestamp.from(generatedAt));
        return java.util.Objects.requireNonNull(
                jdbc.queryForObject(
                        "select id from shopping_list where household_id = ? and start_date = ? for update",
                        UUID.class,
                        ownerId,
                        startDate),
                "list row must exist after insert");
    }

    /** Replaces a list's items, in the order given, and when it was generated. */
    public void replaceItems(UUID listId, Instant generatedAt, List<ShoppingListItemDraft> items) {
        jdbc.update("update shopping_list set generated_at = ? where id = ?", Timestamp.from(generatedAt), listId);
        jdbc.update("delete from shopping_list_item where shopping_list_id = ?", listId);
        int position = 0;
        for (ShoppingListItemDraft item : items) {
            insertItem(listId, position++, item);
        }
    }

    /** Adds an item after the list's last one and returns its id. */
    public UUID appendItem(UUID listId, ShoppingListItemDraft item) {
        Integer last = jdbc.queryForObject(
                "select max(position) from shopping_list_item where shopping_list_id = ?", Integer.class, listId);
        return insertItem(listId, last == null ? 0 : last + 1, item);
    }

    /** How many items the list has. */
    public int countItems(UUID listId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from shopping_list_item where shopping_list_id = ?", Integer.class, listId);
        return count == null ? 0 : count;
    }

    public void deleteItem(UUID itemId) {
        jdbc.update("delete from shopping_list_item where id = ?", itemId);
    }

    private UUID insertItem(UUID listId, int position, ShoppingListItemDraft item) {
        return java.util.Objects.requireNonNull(
                jdbc.queryForObject(
                        """
                        insert into shopping_list_item
                            (shopping_list_id, position, name, unit, quantity, category, checked, added)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        returning id
                        """,
                        UUID.class,
                        listId,
                        position,
                        item.name(),
                        item.unit(),
                        item.quantity(),
                        item.category(),
                        item.checked(),
                        item.added()),
                "insert must return the item id");
    }

    /** The owner's generated list for the week, if any. */
    public Optional<ShoppingList> findList(UUID ownerId, LocalDate startDate) {
        List<UUID> ids = jdbc.queryForList(
                "select id from shopping_list where household_id = ? and start_date = ?",
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
                        "select start_date from shopping_list where household_id = ? order by start_date",
                        LocalDate.class,
                        ownerId)
                .stream()
                .flatMap(startDate -> findList(ownerId, startDate).stream())
                .toList();
    }

    /** Deletes every list the owner has, with their items. */
    public void deleteAllLists(UUID ownerId) {
        jdbc.update("delete from shopping_list where household_id = ?", ownerId);
    }

    /** Whether any of the owner's lists has an item (an empty generated list holds nothing). */
    public boolean hasItems(UUID ownerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from shopping_list_item i
                    join shopping_list l on l.id = i.shopping_list_id
                    where l.household_id = ?)
                """, Boolean.class, ownerId));
    }

    /** The item if it belongs to the owner's list for the week. */
    public Optional<ShoppingList.ShoppingListItem> findItem(UUID ownerId, LocalDate startDate, UUID itemId) {
        List<ShoppingList.ShoppingListItem> items = jdbc.query("""
                select i.id, i.name, i.unit, i.quantity, i.category, i.checked, i.added
                from shopping_list_item i
                join shopping_list l on l.id = i.shopping_list_id
                where l.household_id = ? and l.start_date = ? and i.id = ?
                """, ITEM_MAPPER, ownerId, startDate, itemId);
        return items.isEmpty() ? Optional.empty() : Optional.of(items.getFirst());
    }

    public void updateChecked(UUID itemId, boolean checked) {
        jdbc.update("update shopping_list_item set checked = ? where id = ?", checked, itemId);
    }

    private static Instant requireNonNullTimestamp(@Nullable Timestamp value) {
        return java.util.Objects.requireNonNull(value, "generated_at must not be null")
                .toInstant();
    }

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
                    rs.getBoolean("checked"),
                    rs.getBoolean("added"));
        }
    }
}
