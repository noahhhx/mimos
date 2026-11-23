package io.github.noahhhx.mimos.recipes.collection;

import io.github.noahhhx.mimos.recipes.recipe.PictureContent;
import io.github.noahhhx.mimos.recipes.recipe.PictureFormat;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** The recipe_collection tables (V24): collections, their tags, picks, cover, and each person's default. */
@Repository
class RecipeCollectionRepository {

    private static final RowMapper<Row> ROW = (rs, rowNum) -> new Row(
            rs.getObject("id", UUID.class),
            rs.getObject("household_id", UUID.class),
            rs.getString("name"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getObject("picture_id", UUID.class));

    private static final String SELECT = """
            select c.id, c.household_id, c.name, c.created_at, p.id as picture_id
            from recipe_collection c left join recipe_collection_picture p on p.collection_id = c.id
            """;

    private final JdbcTemplate jdbc;

    RecipeCollectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The ids of a collection's members: the owner's recipes and library
     * recipes that are picked into it or carry any of its tags, ignoring the
     * tag's case. This is the only statement of the membership rule.
     */
    List<UUID> memberIds(UUID ownerId, UUID collectionId) {
        return jdbc.query("""
                select r.id from recipe r
                where (r.household_id = ? or r.household_id is null)
                  and (exists (select 1 from recipe_collection_recipe p
                               where p.collection_id = ? and p.recipe_id = r.id)
                       or exists (select 1 from recipe_tag t
                                  join recipe_collection_tag c on c.tag = lower(t.tag)
                                  where c.collection_id = ? and t.recipe_id = r.id))
                """, (rs, row) -> rs.getObject(1, UUID.class), ownerId, collectionId, collectionId);
    }

    /** The owner's collections, oldest first. */
    List<Row> findOwned(UUID ownerId) {
        return jdbc.query(SELECT + " where c.household_id = ? order by c.created_at, c.id", ROW, ownerId);
    }

    Optional<Row> find(UUID ownerId, UUID collectionId) {
        return jdbc.query(SELECT + " where c.household_id = ? and c.id = ?", ROW, ownerId, collectionId).stream()
                .findFirst();
    }

    boolean existsOwnedBy(UUID ownerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from recipe_collection where household_id = ?)", Boolean.class, ownerId));
    }

    /** Locks the collection's row for the rest of the transaction, so writes to one collection run one at a time. */
    void lock(UUID collectionId) {
        jdbc.query("select id from recipe_collection where id = ? for update", rs -> {}, collectionId);
    }

    void insert(UUID id, UUID ownerId, String name, Instant createdAt) {
        try {
            jdbc.update(
                    "insert into recipe_collection (id, household_id, name, created_at) values (?, ?, ?, ?)",
                    id,
                    ownerId,
                    name,
                    Timestamp.from(createdAt));
        } catch (DuplicateKeyException e) {
            throw new CollectionNameTakenException(name);
        }
    }

    void rename(UUID id, String name) {
        try {
            jdbc.update("update recipe_collection set name = ? where id = ?", name, id);
        } catch (DuplicateKeyException e) {
            throw new CollectionNameTakenException(name);
        }
    }

    /** Replaces the collection's tags and picks. */
    void replaceContents(UUID id, List<String> tags, List<UUID> recipeIds) {
        jdbc.update("delete from recipe_collection_tag where collection_id = ?", id);
        jdbc.update("delete from recipe_collection_recipe where collection_id = ?", id);
        jdbc.batchUpdate(
                "insert into recipe_collection_tag (collection_id, tag) values (?, ?)",
                tags.stream().map(tag -> new Object[] {id, tag}).toList());
        jdbc.batchUpdate(
                "insert into recipe_collection_recipe (collection_id, recipe_id) values (?, ?)",
                recipeIds.stream().map(recipeId -> new Object[] {id, recipeId}).toList());
    }

    /** Deletes a collection with its tags, picks, cover, and anyone's default on it. */
    void delete(UUID id) {
        jdbc.update("delete from recipe_collection where id = ?", id);
    }

    void deleteOwned(UUID ownerId) {
        jdbc.update("delete from recipe_collection where household_id = ?", ownerId);
    }

    Map<UUID, List<String>> tags(List<UUID> ids) {
        return children(
                "select collection_id, tag from recipe_collection_tag where collection_id in (%s) order by tag",
                ids, String.class);
    }

    Map<UUID, List<UUID>> picks(List<UUID> ids) {
        return children(
                "select collection_id, recipe_id from recipe_collection_recipe where collection_id in (%s)"
                        + " order by recipe_id",
                ids, UUID.class);
    }

    private <T> Map<UUID, List<T>> children(String sql, List<UUID> ids, Class<T> type) {
        Map<UUID, List<T>> byCollection = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return byCollection;
        }
        jdbc.query(
                sql.formatted(String.join(",", Collections.nCopies(ids.size(), "?"))),
                (RowCallbackHandler) rs -> byCollection
                        .computeIfAbsent(rs.getObject(1, UUID.class), id -> new ArrayList<>())
                        .add(rs.getObject(2, type)),
                ids.toArray());
        return byCollection;
    }

    /** The collection a person opens Recipes on, if it is one of the owner's. */
    Optional<UUID> defaultOf(UUID ownerId, UUID personId) {
        return jdbc.query("""
                        select d.collection_id from recipe_collection_default d
                        join recipe_collection c on c.id = d.collection_id
                        where d.profile_id = ? and c.household_id = ?
                        """, (rs, row) -> rs.getObject(1, UUID.class), personId, ownerId).stream()
                .findFirst();
    }

    void setDefault(UUID personId, UUID collectionId) {
        jdbc.update("""
                insert into recipe_collection_default (profile_id, collection_id) values (?, ?)
                on conflict (profile_id) do update set collection_id = excluded.collection_id
                """, personId, collectionId);
    }

    void clearDefault(UUID personId, UUID collectionId) {
        jdbc.update(
                "delete from recipe_collection_default where profile_id = ? and collection_id = ?",
                personId,
                collectionId);
    }

    /** Sets the collection's cover, replacing any it had. */
    void putPicture(UUID collectionId, UUID pictureId, PictureFormat format, byte[] bytes) {
        jdbc.update("""
                insert into recipe_collection_picture (collection_id, id, format, bytes) values (?, ?, ?, ?)
                on conflict (collection_id) do update set id = excluded.id, format = excluded.format,
                                                          bytes = excluded.bytes
                """, collectionId, pictureId, format.name(), bytes);
    }

    void deletePicture(UUID collectionId) {
        jdbc.update("delete from recipe_collection_picture where collection_id = ?", collectionId);
    }

    Optional<PictureContent> picture(UUID collectionId, UUID pictureId) {
        return jdbc
                .query(
                        "select format, bytes from recipe_collection_picture where collection_id = ? and id = ?",
                        (rs, row) ->
                                new PictureContent(PictureFormat.valueOf(rs.getString("format")), rs.getBytes("bytes")),
                        collectionId,
                        pictureId)
                .stream()
                .findFirst();
    }

    record Row(
            UUID id,
            UUID ownerId,
            String name,
            Instant createdAt,
            @Nullable UUID pictureId) {}
}
