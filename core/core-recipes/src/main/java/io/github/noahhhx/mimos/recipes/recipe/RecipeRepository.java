package io.github.noahhhx.mimos.recipes.recipe;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC persistence for recipes and their ingredient/step/tag children. The
 * module owns these tables (ADR-0001); nothing outside core-recipes touches
 * them. Child rows are loaded in batches to avoid per-recipe queries.
 * Calculated nutrition is worked out here, as recipes are read, so every
 * reader sees the catalog's current values (ADR-0015) and the current
 * nutrition of the recipes a recipe uses as ingredients (ADR-0018).
 */
@Repository
public class RecipeRepository {

    private static final String SELECT_BASE = """
            select id, household_id, created_by_profile_id, slug, title, description, servings, prep_minutes,
                   cook_minutes, calories, protein_g, carbs_g, fat_g, nutrition_source, created_at, updated_at
            from recipe
            """;

    private static final String INSERT_RECIPE = """
            insert into recipe (id, household_id, created_by_profile_id, slug, title, description, servings,
                                prep_minutes, cook_minutes, calories, protein_g, carbs_g, fat_g, nutrition_source,
                                created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_RECIPE = """
            update recipe set title = ?, description = ?, servings = ?, prep_minutes = ?, cook_minutes = ?,
                              calories = ?, protein_g = ?, carbs_g = ?, fat_g = ?, nutrition_source = ?,
                              updated_at = ?
            where id = ?
            """;

    private final JdbcTemplate jdbc;
    private final IngredientCatalog catalog;

    public RecipeRepository(JdbcTemplate jdbc, IngredientCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    /** Inserts a fully-formed recipe (id and timestamps supplied by the caller). */
    public void insert(Recipe recipe) {
        jdbc.update(
                INSERT_RECIPE,
                recipe.id(),
                recipe.ownerId(),
                recipe.createdByProfileId(),
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
                recipe.nutritionSource().name(),
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
                recipe.nutritionSource().name(),
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

    /** The recipes an owner owns, newest first, optionally filtered by a search term. */
    public List<Recipe> findOwnedBy(UUID ownerId, @Nullable String query) {
        Sql sql = searchSql(" where household_id = ?", List.of(ownerId), query);
        return loadAll(sql.text() + " order by created_at desc", sql.args());
    }

    public boolean existsOwnedBy(UUID ownerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from recipe where household_id = ?)", Boolean.class, ownerId));
    }

    /** Curated library recipes, by title, optionally filtered by a search term. */
    public List<Recipe> findLibrary(@Nullable String query) {
        Sql sql = searchSql(" where household_id is null", List.of(), query);
        return loadAll(sql.text() + " order by title", sql.args());
    }

    public Optional<Recipe> findBySlug(String slug) {
        List<Recipe> found = loadAll(SELECT_BASE + " where slug = ?", List.of(slug));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    /**
     * The first of {@code recipeIds} that uses {@code target} as an
     * ingredient, directly or through other recipes (ADR-0018).
     */
    public Optional<UUID> findFirstUsing(Collection<UUID> recipeIds, UUID target) {
        if (recipeIds.isEmpty()) {
            return Optional.empty();
        }
        List<UUID> found = jdbc.queryForList(
                """
                with recursive reachable(root, id) as (
                    select id, id from recipe where id in (%s)
                    union
                    select reachable.root, line.linked_recipe_id
                    from reachable join recipe_ingredient line on line.recipe_id = reachable.id
                    where line.linked_recipe_id is not null
                )
                select root from reachable where id = ? limit 1
                """.formatted(placeholders(recipeIds.size())),
                UUID.class,
                concat(List.copyOf(recipeIds), List.of(target)).toArray());
        return found.stream().findFirst();
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
            ingredientRows.add(new Object[] {
                recipe.id(),
                i,
                ingredient.quantity(),
                ingredient.unit(),
                ingredient.name(),
                ingredient.note(),
                ingredient.catalogSlug(),
                ingredient.recipeId()
            });
        }
        jdbc.batchUpdate(
                "insert into recipe_ingredient"
                        + " (recipe_id, position, quantity, unit, name, note, catalog_slug, linked_recipe_id)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?)",
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

    /** Builds the search fragment (title/description/tag/ingredient name, case-insensitive) when a term is given. */
    private static Sql searchSql(String visibility, List<Object> args, @Nullable String query) {
        if (query == null || query.isBlank()) {
            return new Sql(SELECT_BASE + visibility, args);
        }
        String pattern = "%" + query.strip() + "%";
        return new Sql(SELECT_BASE + visibility + """
                         and (title ilike ? or description ilike ?
                              or exists (select 1 from recipe_tag t where t.recipe_id = recipe.id and t.tag ilike ?)
                              or exists (select 1 from recipe_ingredient i
                                         where i.recipe_id = recipe.id and i.name ilike ?))
                        """, concat(args, List.of(pattern, pattern, pattern, pattern)));
    }

    private static List<Object> concat(List<?> first, List<?> second) {
        List<Object> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private record Sql(String text, List<Object> args) {}

    /** Runs a recipe query and hydrates children in three batch queries, plus what calculated nutrition needs. */
    private List<Recipe> loadAll(String sql, List<Object> args) {
        List<RecipeRow> rows = jdbc.query(sql, RECIPE_ROW_MAPPER, args.toArray());
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(RecipeRow::id).toList();
        Map<UUID, List<Ingredient>> ingredients = loadIngredients(ids);
        Map<UUID, List<RecipeStep>> steps = loadSteps(ids);
        Map<UUID, List<String>> tags = loadTags(ids);
        Map<UUID, Nutrition> nutrition = perServing(rows, ingredients);
        return rows.stream()
                .map(row -> new Recipe(
                        row.id(),
                        row.ownerId(),
                        row.createdByProfileId(),
                        row.slug(),
                        row.title(),
                        row.description(),
                        row.servings(),
                        row.prepMinutes(),
                        row.cookMinutes(),
                        nutrition.getOrDefault(row.id(), Nutrition.UNKNOWN),
                        row.nutritionSource(),
                        tags.getOrDefault(row.id(), List.of()),
                        ingredients.getOrDefault(row.id(), List.of()),
                        steps.getOrDefault(row.id(), List.of()),
                        row.createdAt(),
                        row.updatedAt()))
                .toList();
    }

    /**
     * Per-serving nutrition of the rows. The recipes that calculated ones
     * link to are loaded a level of links at a time (rows and lines only),
     * then the catalog entries of all of them at once.
     */
    private Map<UUID, Nutrition> perServing(List<RecipeRow> rows, Map<UUID, List<Ingredient>> ingredients) {
        Map<UUID, NutritionCalculator.Node> graph = new HashMap<>();
        List<RecipeRow> level = rows;
        Map<UUID, List<Ingredient>> levelLines = ingredients;
        while (!level.isEmpty()) {
            for (RecipeRow row : level) {
                graph.put(
                        row.id(),
                        new NutritionCalculator.Node(
                                row.servings(),
                                row.nutritionSource(),
                                row.nutrition(),
                                levelLines.getOrDefault(row.id(), List.of())));
            }
            List<UUID> linked = calculatedLines(level.stream().map(RecipeRow::id), graph)
                    .map(Ingredient::recipeId)
                    .filter(Objects::nonNull)
                    .filter(id -> !graph.containsKey(id))
                    .distinct()
                    .toList();
            if (linked.isEmpty()) {
                break;
            }
            level = jdbc.query(
                    SELECT_BASE + " where id in (" + placeholders(linked.size()) + ")",
                    RECIPE_ROW_MAPPER,
                    linked.toArray());
            levelLines = loadIngredients(level.stream().map(RecipeRow::id).toList());
        }
        Map<String, CatalogIngredient> entries = catalog.findBySlugs(calculatedLines(graph.keySet().stream(), graph)
                .map(Ingredient::catalogSlug)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return NutritionCalculator.perServing(graph, entries);
    }

    /** The lines of the calculated recipes among {@code ids}: the only lines nutrition reads. */
    private static Stream<Ingredient> calculatedLines(Stream<UUID> ids, Map<UUID, NutritionCalculator.Node> graph) {
        return ids.map(graph::get)
                .filter(node -> node != null && node.source() == NutritionSource.INGREDIENTS)
                .flatMap(node -> node.lines().stream());
    }

    private Map<UUID, List<Ingredient>> loadIngredients(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .query(
                        "select recipe_id, position, quantity, unit, name, note, catalog_slug, linked_recipe_id"
                                + " from recipe_ingredient where recipe_id in ("
                                + placeholders(ids.size()) + ") order by recipe_id, position",
                        (rs, i) -> Map.entry(
                                rs.getObject("recipe_id", UUID.class),
                                new Ingredient(
                                        nullableDouble(rs, "quantity"),
                                        rs.getString("unit"),
                                        rs.getString("name"),
                                        rs.getString("note"),
                                        rs.getString("catalog_slug"),
                                        rs.getObject("linked_recipe_id", UUID.class))),
                        ids.toArray())
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.computeIfAbsent(entry.getKey(), id -> new ArrayList<>())
                                .add(entry.getValue()),
                        Map::putAll);
    }

    private Map<UUID, List<RecipeStep>> loadSteps(List<UUID> ids) {
        return jdbc
                .query(
                        "select recipe_id, position, instruction from recipe_step where recipe_id in ("
                                + placeholders(ids.size()) + ") order by recipe_id, position",
                        (rs, i) -> Map.entry(
                                rs.getObject("recipe_id", UUID.class), new RecipeStep(rs.getString("instruction"))),
                        ids.toArray())
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.computeIfAbsent(entry.getKey(), id -> new ArrayList<>())
                                .add(entry.getValue()),
                        Map::putAll);
    }

    private Map<UUID, List<String>> loadTags(List<UUID> ids) {
        return jdbc
                .query(
                        "select recipe_id, tag from recipe_tag where recipe_id in (" + placeholders(ids.size())
                                + ") order by recipe_id, tag",
                        (rs, i) -> Map.entry(rs.getObject("recipe_id", UUID.class), rs.getString("tag")),
                        ids.toArray())
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.computeIfAbsent(entry.getKey(), id -> new ArrayList<>())
                                .add(entry.getValue()),
                        Map::putAll);
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private record RecipeRow(
            UUID id,
            @Nullable UUID ownerId,
            @Nullable UUID createdByProfileId,
            @Nullable String slug,
            String title,
            String description,
            int servings,
            @Nullable Integer prepMinutes,
            @Nullable Integer cookMinutes,
            Nutrition nutrition,
            NutritionSource nutritionSource,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {}

    private static final RowMapper<RecipeRow> RECIPE_ROW_MAPPER = new RecipeRowMapper();

    private static final class RecipeRowMapper implements RowMapper<RecipeRow> {

        @Override
        public RecipeRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RecipeRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("household_id", UUID.class),
                    rs.getObject("created_by_profile_id", UUID.class),
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
                    NutritionSource.valueOf(rs.getString("nutrition_source")),
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
