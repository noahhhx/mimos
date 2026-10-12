package io.github.noahhhx.mimos.recipes.recipe;

import java.util.UUID;

/**
 * Where picture bytes live (ADR-0023). Knows picture ids and bytes, nothing
 * about recipes, owners, formats, or limits: {@link RecipePictureService}
 * decides all of that before calling it.
 *
 * <p>There is no delete. A picture exists while its recipe_picture row does,
 * and bytes without a row are garbage. The Postgres store gets deletion from
 * the foreign key cascade (removing a picture, deleting its recipe). A store
 * outside the database will owe a sweep that removes objects with no row,
 * because a recipe delete cascades past any Java code.
 */
public interface PictureStore {

    /**
     * Stores the bytes of the picture whose row was inserted earlier in the
     * current transaction. A transactional store commits or rolls back with
     * it; any other store may leave an orphan on rollback, which the sweep
     * removes.
     */
    void put(UUID pictureId, byte[] bytes);

    /** The bytes of a picture that has a row; NoSuchElementException if they are missing. */
    byte[] get(UUID pictureId);
}
