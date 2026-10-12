package io.github.noahhhx.mimos.recipes.recipe;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** What pictures each recipe has, in recipe_picture (V16). Never reads bytes; those belong to the PictureStore. */
@Repository
class RecipePictureRepository {

    private static final RowMapper<RecipePicture> PICTURE = (rs, rowNum) ->
            new RecipePicture(rs.getObject("id", UUID.class), PictureFormat.valueOf(rs.getString("format")));

    private final JdbcTemplate jdbc;

    RecipePictureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Locks the recipe's row for the rest of the transaction, so adds to one recipe run one at a time. */
    void lockRecipe(UUID recipeId) {
        jdbc.query("select id from recipe where id = ? for update", rs -> {}, recipeId);
    }

    List<RecipePicture> findByRecipe(UUID recipeId) {
        return jdbc.query(
                "select id, format from recipe_picture where recipe_id = ? order by position", PICTURE, recipeId);
    }

    Optional<RecipePicture> find(UUID recipeId, UUID pictureId) {
        return jdbc
                .query(
                        "select id, format from recipe_picture where recipe_id = ? and id = ?",
                        PICTURE,
                        recipeId,
                        pictureId)
                .stream()
                .findFirst();
    }

    int count(UUID recipeId) {
        Integer count =
                jdbc.queryForObject("select count(*) from recipe_picture where recipe_id = ?", Integer.class, recipeId);
        return count == null ? 0 : count;
    }

    /** Inserts a picture after the recipe's last one. */
    void insert(UUID recipeId, RecipePicture picture, Instant createdAt) {
        jdbc.update("""
                insert into recipe_picture (id, recipe_id, position, format, created_at)
                select ?, ?, coalesce(max(position), -1) + 1, ?, ? from recipe_picture where recipe_id = ?
                """, picture.id(), recipeId, picture.format().name(), Timestamp.from(createdAt), recipeId);
    }

    /** Deletes one picture of one recipe, its bytes with it; false when it was not that recipe's. */
    boolean delete(UUID recipeId, UUID pictureId) {
        return jdbc.update("delete from recipe_picture where recipe_id = ? and id = ?", recipeId, pictureId) > 0;
    }
}
