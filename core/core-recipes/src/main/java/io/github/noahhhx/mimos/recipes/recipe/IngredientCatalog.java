package io.github.noahhhx.mimos.recipes.recipe;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC persistence for the ingredient catalog: the shared entries seeded
 * for everyone (ADR-0015) and each user's own (ADR-0016), keyed by slug.
 */
@Repository
public class IngredientCatalog {

    private static final String SELECT =
            "select slug, household_id, name, basis, calories, protein_g, carbs_g, fat_g from catalog_ingredient";

    private static final RowMapper<CatalogIngredient> MAPPER = (rs, rowNum) -> new CatalogIngredient(
            rs.getString("slug"),
            rs.getObject("household_id", UUID.class),
            rs.getString("name"),
            NutritionBasis.valueOf(rs.getString("basis")),
            rs.getBigDecimal("calories").doubleValue(),
            rs.getBigDecimal("protein_g").doubleValue(),
            rs.getBigDecimal("carbs_g").doubleValue(),
            rs.getBigDecimal("fat_g").doubleValue());

    private final JdbcTemplate jdbc;

    public IngredientCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The shared entries and the owner's own, by name. */
    public List<CatalogIngredient> findVisibleTo(UUID ownerId) {
        return jdbc.query(
                SELECT + " where household_id is null or household_id = ? order by lower(name), slug", MAPPER, ownerId);
    }

    /** The owner's own entries, by name. */
    public List<CatalogIngredient> findOwnedBy(UUID ownerId) {
        return jdbc.query(SELECT + " where household_id = ? order by lower(name), slug", MAPPER, ownerId);
    }

    /** The entries with these slugs, whoever owns them; unknown slugs are omitted. */
    public Map<String, CatalogIngredient> findBySlugs(Collection<String> slugs) {
        if (slugs.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .query(
                        SELECT + " where slug in (" + String.join(",", Collections.nCopies(slugs.size(), "?")) + ")",
                        MAPPER,
                        slugs.toArray())
                .stream()
                .collect(Collectors.toMap(CatalogIngredient::slug, Function.identity()));
    }

    /** Inserts a shared entry, or refreshes the shared entry with its slug. */
    public void upsertShared(CatalogIngredient entry) {
        jdbc.update(
                """
                insert into catalog_ingredient (slug, name, basis, calories, protein_g, carbs_g, fat_g)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (slug) do update set name = excluded.name, basis = excluded.basis,
                    calories = excluded.calories, protein_g = excluded.protein_g,
                    carbs_g = excluded.carbs_g, fat_g = excluded.fat_g
                where catalog_ingredient.household_id is null
                """,
                entry.slug(),
                entry.name(),
                entry.basis().name(),
                entry.calories(),
                entry.proteinG(),
                entry.carbsG(),
                entry.fatG());
    }

    public void insert(CatalogIngredient entry) {
        jdbc.update(
                """
                insert into catalog_ingredient (slug, household_id, name, basis, calories, protein_g, carbs_g, fat_g)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entry.slug(),
                entry.ownerId(),
                entry.name(),
                entry.basis().name(),
                entry.calories(),
                entry.proteinG(),
                entry.carbsG(),
                entry.fatG());
    }

    public void update(CatalogIngredient entry) {
        jdbc.update(
                "update catalog_ingredient set name = ?, basis = ?, calories = ?, protein_g = ?, carbs_g = ?,"
                        + " fat_g = ? where slug = ?",
                entry.name(),
                entry.basis().name(),
                entry.calories(),
                entry.proteinG(),
                entry.carbsG(),
                entry.fatG(),
                entry.slug());
    }

    public void delete(String slug) {
        jdbc.update("delete from catalog_ingredient where slug = ?", slug);
    }

    public boolean existsOwnedBy(UUID ownerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from catalog_ingredient where household_id = ?)", Boolean.class, ownerId));
    }

    /** Gives every entry one owner owns to another; slugs, and so every link to them, stay. */
    public void moveOwned(UUID fromOwnerId, UUID toOwnerId) {
        jdbc.update("update catalog_ingredient set household_id = ? where household_id = ?", toOwnerId, fromOwnerId);
    }
}
