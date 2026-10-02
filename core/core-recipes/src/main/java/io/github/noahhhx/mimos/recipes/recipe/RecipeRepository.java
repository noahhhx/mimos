package io.github.noahhhx.mimos.recipes.recipe;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC persistence for recipes and their ingredient/step/tag children. The
 * module owns these tables (ADR-0001); nothing outside core-recipes touches
 * them. Child rows are loaded in batches to avoid per-recipe queries.
 */
@Repository
public class RecipeRepository {

    private static final String SELECT_BASE = """
            select id, owner_profile_id, slug, title, description, servings, prep_minutes, cook_minutes,
                   calories, protein_g, carbs_g, fat_g, created_at, updated_at
            from recipe
            """;

    private static final String INSERT_RECIPE = """
            insert into recipe (id, owner_profile_id, slug, title, description, servings, prep_minutes, cook_minutes,
                                calories, protein_g, carbs_g, fat_g, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_RECIPE = """
            update recipe set title = ?, description = ?, servings = ?, prep_minutes = ?, cook_minutes = ?,
                              calories = ?, protein_g = ?, carbs_g = ?, fat_g = ?, updated_at = ?
            where id = ?
            """;

    private final JdbcTemplate jdbc;

    public RecipeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts a fully-formed recipe (id and timestamps supplied by the caller). */
    public void insert(Recipe recipe) {
        jdbc.update(
                INSERT_RECIPE,
                recipe.id(),
                recipe.ownerProfileId(),
                recipe.slug(),
                recipe.title(),
                recipe.description(),
                recipe.servings(),
                recipe.prepMinutes(),
                recipe.cookMinutes(),
                recipe.nutrition().calories(),
                recipe.nutrition().proteinG(),
                recipe.nutrition().carbsG(),
                recipe.nutrition().fatG(),
                Timestamp.from(recipe.createdAt()),
                Timestamp.from(recipe.updatedAt()));
        insertChildren(recipe);
    }

    /** Replaces content and children of an existing recipe (slug and owner are immutable). */
    public void replace(Recipe recipe) {
        jdbc.update(
                UPDATE_RECIPE,
                recipe.title(),
                recipe.description(),
                recipe.servings(),
                recipe.prepMinutes(),
                recipe.cookMinutes(),
                recipe.nutrition().calories(),
                recipe.nutrition().proteinG(),
                recipe.nutrition().carbsG(),
                recipe.nutrition().fatG(),
                Timestamp.from(recipe.updatedAt()),
                recipe.id());
        jdbc.update("delete from recipe_ingredient where recipe_id = ?", recipe.id());
        jdbc.update("delete from recipe_step where recipe_id = ?", recipe.id());
        jdbc.update("delete from recipe_tag where recipe_id = ?", recipe.id());
        insertChildren(recipe);
    }

    /** Deletes a recipe (children cascade); true if a row was removed. */
    public boolean deleteById(UUID id) {
        return jdbc.update("delete from recipe where id = ?", id) > 0;
    }

    public Optional<Recipe> findById(UUID id) {
        List<Recipe> found = loadAll(SELECT_BASE + " where id = ?", List.of(id));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    /** The recipes a profile owns, newest first, optionally filtered by a search term. */
    public List<Recipe> findOwnedBy(UUID ownerProfileId, @Nullable String query) {
        Sql sql = searchSql(" where owner_profile_id = ?", List.of(ownerProfileId), query);
        return loadAll(sql.text() + " order by created_at desc", sql.args());
    }

    public boolean existsOwnedBy(UUID ownerProfileId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from recipe where owner_profile_id = ?)", Boolean.class, ownerProfileId));
    }

    /** Curated library recipes, by title, optionally filtered by a search term. */
    public List<Recipe> findLibrary(@Nullable String query) {
        Sql sql = searchSql(" where owner_profile_id is null", List.of(), query);
        return loadAll(sql.text() + " order by title", sql.args());
    }

    public Optional<Recipe> findBySlug(String slug) {
        List<Recipe> found = loadAll(SELECT_BASE + " where slug = ?", List.of(slug));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    /** Loads recipes by id, in the order requested; missing ids are omitted. */
    public Map<UUID, Recipe> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = ids.stream().distinct().toList();
        List<Recipe> recipes = loadAll(
                SELECT_BASE + " where id in (" + placeholders(distinct.size()) + ")", List.of(distinct.toArray()));
        Map<UUID, Recipe> byId = new LinkedHashMap<>();
        for (Recipe recipe : recipes) {
            byId.put(recipe.id(), recipe);
        }
        return byId;
    }

    private void insertChildren(Recipe recipe) {
        List<Object[]> ingredientRows = new ArrayList<>();
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            Ingredient ingredient = recipe.ingredients().get(i);
            ingredientRows.add(
                    new Object[] {recipe.id(), i, ingredient.quantity(), ingredient.unit(), ingredient.name()});
        }
        jdbc.batchUpdate(
                "insert into recipe_ingredient (recipe_id, position, quantity, unit, name) values (?, ?, ?, ?, ?)",
                ingredientRows);
        List<Object[]> stepRows = new ArrayList<>();
        for (int i = 0; i < recipe.steps().size(); i++) {
            stepRows.add(new Object[] {recipe.id(), i, recipe.steps().get(i).instruction()});
        }
        jdbc.batchUpdate("insert into recipe_step (recipe_id, position, instruction) values (?, ?, ?)", stepRows);
        List<Object[]> tagRows = recipe.tags().stream()
                .map(tag -> new Object[] {recipe.id(), tag})
                .toList();
        jdbc.batchUpdate("insert into recipe_tag (recipe_id, tag) values (?, ?)", tagRows);
    }

    /** Builds the search fragment (title/description/tag, case-insensitive) when a term is given. */
    private static Sql searchSql(String visibility, List<Object> args, @Nullable String query) {
        if (query == null || query.isBlank()) {
            return new Sql(SELECT_BASE + visibility, args);
        }
        String pattern = "%" + query.strip() + "%";
        return new Sql(SELECT_BASE + visibility + """
                         and (title ilike ? or description ilike ?
                              or exists (select 1 from recipe_tag t where t.recipe_id = recipe.id and t.tag ilike ?))
                        """, concat(args, List.of(pattern, pattern, pattern)));
    }

    private static List<Object> concat(List<Object> first, List<Object> second) {
        List<Object> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private record Sql(String text, List<Object> args) {}

    /** Runs a recipe query and hydrates children in three batch queries. */
    private List<Recipe> loadAll(String sql, List<Object> args) {
        List<RecipeRow> rows = jdbc.query(sql, RECIPE_ROW_MAPPER, args.toArray());
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Ingredient>> ingredients = loadIngredients(rows);
        Map<UUID, List<RecipeStep>> steps = loadSteps(rows);
        Map<UUID, List<String>> tags = loadTags(rows);
        return rows.stream()
                .map(row -> new Recipe(
                        row.id(),
                        row.ownerProfileId(),
                        row.slug(),
                        row.title(),
                        row.description(),
                        row.servings(),
                        row.prepMinutes(),
                        row.cookMinutes(),
                        row.nutrition(),
                        tags.getOrDefault(row.id(), List.of()),
                        ingredients.getOrDefault(row.id(), List.of()),
                        steps.getOrDefault(row.id(), List.of()),
                        row.createdAt(),
                        row.updatedAt()))
                .toList();
    }

    private Map<UUID, List<Ingredient>> loadIngredients(List<RecipeRow> rows) {
        return jdbc
                .query(
                        "select recipe_id, position, quantity, unit, name from recipe_ingredient where recipe_id in ("
                                + placeholders(rows.size()) + ") order by recipe_id, position",
                        (rs, i) -> Map.entry(
                                rs.getObject("recipe_id", UUID.class),
                                new Ingredient(
                                        nullableDouble(rs, "quantity"), rs.getString("unit"), rs.getString("name"))),
                        rowIds(rows))
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.computeIfAbsent(entry.getKey(), id -> new ArrayList<>())
                                .add(entry.getValue()),
                        Map::putAll);
    }

    private Map<UUID, List<RecipeStep>> loadSteps(List<RecipeRow> rows) {
        return jdbc
                .query(
                        "select recipe_id, position, instruction from recipe_step where recipe_id in ("
                                + placeholders(rows.size()) + ") order by recipe_id, position",
                        (rs, i) -> Map.entry(
                                rs.getObject("recipe_id", UUID.class), new RecipeStep(rs.getString("instruction"))),
                        rowIds(rows))
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.computeIfAbsent(entry.getKey(), id -> new ArrayList<>())
                                .add(entry.getValue()),
                        Map::putAll);
    }

    private Map<UUID, List<String>> loadTags(List<RecipeRow> rows) {
        return jdbc
                .query(
                        "select recipe_id, tag from recipe_tag where recipe_id in (" + placeholders(rows.size())
                                + ") order by recipe_id, tag",
                        (rs, i) -> Map.entry(rs.getObject("recipe_id", UUID.class), rs.getString("tag")),
                        rowIds(rows))
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.computeIfAbsent(entry.getKey(), id -> new ArrayList<>())
                                .add(entry.getValue()),
                        Map::putAll);
    }

    private static Object[] rowIds(List<RecipeRow> rows) {
        return rows.stream().map(RecipeRow::id).toArray();
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private record RecipeRow(
            UUID id,
            @Nullable UUID ownerProfileId,
            @Nullable String slug,
            String title,
            String description,
            int servings,
            @Nullable Integer prepMinutes,
            @Nullable Integer cookMinutes,
            Nutrition nutrition,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {}

    private static final RowMapper<RecipeRow> RECIPE_ROW_MAPPER = new RecipeRowMapper();

    private static final class RecipeRowMapper implements RowMapper<RecipeRow> {

        @Override
        public RecipeRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RecipeRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("owner_profile_id", UUID.class),
                    rs.getString("slug"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getInt("servings"),
                    nullableInt(rs, "prep_minutes"),
                    nullableInt(rs, "cook_minutes"),
                    new Nutrition(
                            nullableDouble(rs, "calories"),
                            nullableDouble(rs, "protein_g"),
                            nullableDouble(rs, "carbs_g"),
                            nullableDouble(rs, "fat_g")),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant());
        }

        private static @Nullable Integer nullableInt(ResultSet rs, String column) throws SQLException {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        }
    }

    private static @Nullable Double nullableDouble(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }
}
