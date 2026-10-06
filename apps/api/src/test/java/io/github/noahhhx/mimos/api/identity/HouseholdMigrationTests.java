package io.github.noahhhx.mimos.api.identity;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Upgrades a database holding two users' data from V12 to the latest
 * migration and checks that each user's rows now belong to a household of
 * their own (ADR-0019), with nothing lost.
 */
class HouseholdMigrationTests {

    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

    static {
        postgres.start();
    }

    private static final DriverManagerDataSource dataSource =
            new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    private static final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BEN = UUID.randomUUID();
    private static final UUID ANNA_RECIPE = UUID.randomUUID();
    private static final UUID BEN_RECIPE = UUID.randomUUID();
    private static final UUID LIBRARY_RECIPE = UUID.randomUUID();
    private static final UUID ANNA_PLAN = UUID.randomUUID();
    private static final UUID BEN_PLAN = UUID.randomUUID();
    private static final UUID ANNA_LUNCH = UUID.randomUUID();
    private static final UUID ANNA_DINNER = UUID.randomUUID();
    private static final UUID BEN_DINNER = UUID.randomUUID();
    private static final UUID ANNA_LIST = UUID.randomUUID();
    private static final UUID ANNA_LOG = UUID.randomUUID();
    private static final UUID ANNA_SUBJECT = UUID.randomUUID();
    private static final UUID BEN_SUBJECT = UUID.randomUUID();

    @BeforeAll
    static void upgradeFromV12() {
        flyway("12").migrate();
        insertVersion12Data();
        flyway("latest").migrate();
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static void insertVersion12Data() {
        jdbc.update("insert into user_profile (id, subject_id, display_name) values (?, 'anna', 'Anna')", ANNA);
        jdbc.update("insert into user_profile (id, subject_id, display_name) values (?, 'ben', 'Ben')", BEN);
        jdbc.update(
                "insert into recipe (id, owner_profile_id, title, servings) values (?, ?, 'Anna soup', 2)",
                ANNA_RECIPE,
                ANNA);
        jdbc.update(
                "insert into recipe (id, owner_profile_id, title, servings) values (?, ?, 'Ben stew', 4)",
                BEN_RECIPE,
                BEN);
        jdbc.update(
                "insert into recipe (id, slug, title, servings) values (?, 'library-pie', 'Library pie', 6)",
                LIBRARY_RECIPE);
        jdbc.update("""
                insert into catalog_ingredient (slug, owner_profile_id, name, basis, calories, protein_g, carbs_g, fat_g)
                values ('anna-spice-abc123', ?, 'Anna spice', 'PER_100_G', 10, 1, 1, 1)
                """, ANNA);
        jdbc.update("""
                insert into catalog_ingredient (slug, name, basis, calories, protein_g, carbs_g, fat_g)
                values ('salt', 'Salt', 'PER_100_G', 0, 0, 0, 0)
                """);
        jdbc.update(
                "insert into meal_plan (id, owner_profile_id, start_date) values (?, ?, '2026-10-05')",
                ANNA_PLAN,
                ANNA);
        jdbc.update(
                "insert into meal_plan (id, owner_profile_id, start_date) values (?, ?, '2026-10-05')", BEN_PLAN, BEN);
        insertEntry(ANNA_LUNCH, ANNA_PLAN, "LUNCH", ANNA_RECIPE);
        insertEntry(ANNA_DINNER, ANNA_PLAN, "DINNER", LIBRARY_RECIPE);
        insertEntry(BEN_DINNER, BEN_PLAN, "DINNER", BEN_RECIPE);
        jdbc.update(
                "insert into shopping_list (id, owner_profile_id, start_date, generated_at)"
                        + " values (?, ?, '2026-10-05', now())",
                ANNA_LIST,
                ANNA);
        jdbc.update(
                "insert into shopping_list_item (shopping_list_id, position, name, quantity, category)"
                        + " values (?, 0, 'Carrots', 3, 'Produce')",
                ANNA_LIST);
        jdbc.update(
                "insert into meal_log (id, owner_profile_id, log_date, meal_type, recipe_id, description)"
                        + " values (?, ?, '2026-10-06', 'LUNCH', ?, 'Anna soup')",
                ANNA_LOG,
                ANNA,
                ANNA_RECIPE);
        jdbc.update("insert into plugin_opt_in (profile_id, plugin_id) values (?, 'country-week')", ANNA);
        jdbc.update(
                "insert into plugin_subject (profile_id, plugin_id, subject, created_at)"
                        + " values (?, 'country-week', ?, now())",
                ANNA,
                ANNA_SUBJECT);
        jdbc.update(
                "insert into plugin_subject (profile_id, plugin_id, subject, created_at)"
                        + " values (?, 'country-week', ?, now())",
                BEN,
                BEN_SUBJECT);
    }

    private static void insertEntry(UUID id, UUID planId, String mealType, UUID recipeId) {
        jdbc.update(
                "insert into meal_plan_entry (id, meal_plan_id, entry_date, meal_type, recipe_id)"
                        + " values (?, ?, '2026-10-06', ?, ?)",
                id,
                planId,
                mealType,
                recipeId);
    }

    private static UUID householdOf(UUID profileId) {
        return requireNonNull(
                jdbc.queryForObject("select household_id from user_profile where id = ?", UUID.class, profileId));
    }

    @Test
    void everyProfileGetsAHouseholdOfItsOwn() {
        assertThat(householdOf(ANNA)).isNotEqualTo(householdOf(BEN));
        assertThat(jdbc.queryForList("select id from household", UUID.class))
                .containsExactlyInAnyOrder(householdOf(ANNA), householdOf(BEN));
    }

    @Test
    void recipesBelongToTheirOwnersHouseholdAndRecordTheirCreator() {
        assertThat(jdbc.queryForList(
                        "select id, household_id, created_by_profile_id from recipe where household_id is not null"))
                .containsExactlyInAnyOrder(
                        Map.of("id", ANNA_RECIPE, "household_id", householdOf(ANNA), "created_by_profile_id", ANNA),
                        Map.of("id", BEN_RECIPE, "household_id", householdOf(BEN), "created_by_profile_id", BEN));
        assertThat(jdbc.queryForList(
                        "select id from recipe where household_id is null and created_by_profile_id is null",
                        UUID.class))
                .containsExactly(LIBRARY_RECIPE);
    }

    @Test
    void personalIngredientsBelongToTheirOwnersHousehold() {
        assertThat(jdbc.queryForObject(
                        "select household_id from catalog_ingredient where slug = 'anna-spice-abc123'", UUID.class))
                .isEqualTo(householdOf(ANNA));
        assertThat(jdbc.queryForObject("select household_id from catalog_ingredient where slug = 'salt'", UUID.class))
                .isNull();
    }

    @Test
    void plansAndListsBelongToTheirOwnersHousehold() {
        assertThat(jdbc.queryForObject("select household_id from meal_plan where id = ?", UUID.class, ANNA_PLAN))
                .isEqualTo(householdOf(ANNA));
        assertThat(jdbc.queryForObject("select household_id from meal_plan where id = ?", UUID.class, BEN_PLAN))
                .isEqualTo(householdOf(BEN));
        assertThat(jdbc.queryForObject("select household_id from shopping_list where id = ?", UUID.class, ANNA_LIST))
                .isEqualTo(householdOf(ANNA));
        assertThat(jdbc.queryForObject("select count(*) from shopping_list_item", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void everyEntryHasItsPlanOwnerAsItsOnlyDiner() {
        assertThat(jdbc.queryForList("select entry_id, profile_id from meal_plan_entry_diner"))
                .containsExactlyInAnyOrder(
                        Map.of("entry_id", ANNA_LUNCH, "profile_id", ANNA),
                        Map.of("entry_id", ANNA_DINNER, "profile_id", ANNA),
                        Map.of("entry_id", BEN_DINNER, "profile_id", BEN));
    }

    @Test
    void logsStayWithTheirProfile() {
        assertThat(jdbc.queryForList("select id, owner_profile_id, recipe_id from meal_log"))
                .containsExactly(Map.of("id", ANNA_LOG, "owner_profile_id", ANNA, "recipe_id", ANNA_RECIPE));
    }

    @Test
    void pluginOptInsAndSubjectsMoveToTheHouseholdWithSubjectsUnchanged() {
        assertThat(jdbc.queryForList("select household_id, plugin_id from plugin_opt_in"))
                .containsExactly(Map.of("household_id", householdOf(ANNA), "plugin_id", "country-week"));
        assertThat(jdbc.queryForList("select household_id, plugin_id, subject from plugin_subject"))
                .containsExactlyInAnyOrder(
                        Map.of("household_id", householdOf(ANNA), "plugin_id", "country-week", "subject", ANNA_SUBJECT),
                        Map.of("household_id", householdOf(BEN), "plugin_id", "country-week", "subject", BEN_SUBJECT));
    }
}
