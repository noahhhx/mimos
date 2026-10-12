package io.github.noahhhx.mimos.recipes.recipe;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Picture bytes in Postgres (V16): the only reader and writer of recipe_picture_data. */
@Repository
class PostgresPictureStore implements PictureStore {

    private final JdbcTemplate jdbc;

    PostgresPictureStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void put(UUID pictureId, byte[] bytes) {
        jdbc.update("insert into recipe_picture_data (picture_id, bytes) values (?, ?)", pictureId, bytes);
    }

    @Override
    public byte[] get(UUID pictureId) {
        List<byte[]> found = jdbc.query(
                "select bytes from recipe_picture_data where picture_id = ?",
                (rs, rowNum) -> rs.getBytes("bytes"),
                pictureId);
        if (found.isEmpty()) {
            throw new NoSuchElementException("picture not found: " + pictureId);
        }
        return found.getFirst();
    }
}
