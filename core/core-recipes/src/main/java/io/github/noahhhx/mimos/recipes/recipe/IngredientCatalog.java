package io.github.noahhhx.mimos.recipes.recipe;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The instance's shared ingredient catalog (ADR-0015): seeded read-only
 * content, keyed by slug, that recipe lines link to for calculated
 * nutrition.
 */
@Repository
public class IngredientCatalog {

    private static final String SELECT =
            "select slug, name, basis, calories, protein_g, carbs_g, fat_g" + " from catalog_ingredient";

    private static final RowMapper<CatalogIngredient> MAPPER = (rs, rowNum) -> new CatalogIngredient(
            rs.getString("slug"),
            rs.getString("name"),
            NutritionBasis.valueOf(rs.getString("basis")),
            decimal(rs.getBigDecimal("calories")),
            decimal(rs.getBigDecimal("protein_g")),
            decimal(rs.getBigDecimal("carbs_g")),
            decimal(rs.getBigDecimal("fat_g")));

    private final JdbcTemplate jdbc;

    public IngredientCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Catalog entries by name, optionally only those whose name or slug contains the search term. */
    public List<CatalogIngredient> list(@Nullable String query) {
        if (query == null || query.isBlank()) {
            return jdbc.query(SELECT + " order by name", MAPPER);
        }
        String pattern = "%" + query.strip().toLowerCase(java.util.Locale.ROOT) + "%";
        return jdbc.query(SELECT + " where lower(name) like ? or slug like ? order by name", MAPPER, pattern, pattern);
    }

    /** The entries with these slugs; unknown slugs are omitted. */
    public Map<String, CatalogIngredient> findBySlugs(Collection<String> slugs) {
        if (slugs.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .query(
                        SELECT + " where slug in (" + String.join(",", java.util.Collections.nCopies(slugs.size(), "?"))
                                + ")",
                        MAPPER,
                        slugs.toArray())
                .stream()
                .collect(Collectors.toMap(CatalogIngredient::slug, Function.identity()));
    }

    /** Inserts the entry, or refreshes the one with its slug. */
    public void upsert(CatalogIngredient entry) {
        jdbc.update(
                """
                insert into catalog_ingredient (slug, name, basis, calories, protein_g, carbs_g, fat_g)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (slug) do update set name = excluded.name, basis = excluded.basis,
                    calories = excluded.calories, protein_g = excluded.protein_g,
                    carbs_g = excluded.carbs_g, fat_g = excluded.fat_g
                """,
                entry.slug(),
                entry.name(),
                entry.basis().name(),
                entry.calories(),
                entry.proteinG(),
                entry.carbsG(),
                entry.fatG());
    }

    private static double decimal(BigDecimal value) {
        return value.doubleValue();
    }
}
