package io.github.noahhhx.mimos.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.planning.plan.MealType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Card validation rules (ADR-0006): untrusted plugin output is bounded,
 * week-checked, catalog-checked, and truncated before it reaches a user.
 */
class SuggestionServiceValidationTests {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5); // a Monday
    private static final UUID SPAGHETTI = UUID.randomUUID();
    private static final UUID DAHL = UUID.randomUUID();
    private static final PluginManifest MANIFEST = new PluginManifest(
            PluginManifest.SCHEMA,
            "stub-plugin",
            "Stub Plugin",
            "1.0.0",
            List.of("1"),
            List.of("plan-suggestions"),
            null);

    private static SuggestionContext context() {
        return new SuggestionContext(
                MONDAY,
                List.of(new PlannedSlot(MONDAY, MealType.LUNCH, 2, SPAGHETTI)),
                List.of(
                        new LibraryRecipe(SPAGHETTI, "Spaghetti al Pomodoro", List.of("italian"), 4),
                        new LibraryRecipe(DAHL, "Red Lentil Dahl", List.of("curry"), 6)));
    }

    @Test
    void validCardsPassWithTitlesHydratedFromTheCatalog() {
        List<PlanSuggestion> result = SuggestionService.validatedCards(
                MANIFEST,
                List.of(new PluginCard(
                        "Italy week",
                        "Two classics and a weeknight traybake.",
                        "IT",
                        List.of(new PluginCardEntry(
                                MONDAY.plusDays(2).toString(), "DINNER", SPAGHETTI.toString(), 2.0)))),
                context());
        assertThat(result).hasSize(1);
        PlanSuggestion card = result.get(0);
        assertThat(card.pluginId()).isEqualTo("stub-plugin");
        assertThat(card.pluginName()).isEqualTo("Stub Plugin");
        assertThat(card.title()).isEqualTo("Italy week");
        assertThat(card.entries()).hasSize(1);
        assertThat(card.entries().get(0).recipeTitle()).isEqualTo("Spaghetti al Pomodoro");
        assertThat(card.entries().get(0).mealType()).isEqualTo(MealType.DINNER);
        assertThat(card.entries().get(0).servings()).isEqualTo(2.0);
    }

    @Test
    void invalidEntriesAreDroppedAndCardsLeftEmptyAreDropped() {
        List<PlanSuggestion> result = SuggestionService.validatedCards(
                MANIFEST,
                List.of(
                        new PluginCard(
                                "Mixed",
                                null,
                                null,
                                List.of(
                                        // Out of the plan week.
                                        new PluginCardEntry("2026-02-01", "DINNER", SPAGHETTI.toString(), 2.0),
                                        // Not a recipe from the catalog.
                                        new PluginCardEntry(
                                                MONDAY.toString(),
                                                "DINNER",
                                                UUID.randomUUID().toString(),
                                                2.0),
                                        // Not a UUID at all.
                                        new PluginCardEntry(MONDAY.toString(), "DINNER", "nope", 2.0),
                                        // Invalid meal type.
                                        new PluginCardEntry(MONDAY.toString(), "BRUNCH", SPAGHETTI.toString(), 2.0),
                                        // Servings out of the domain bound.
                                        new PluginCardEntry(MONDAY.toString(), "DINNER", SPAGHETTI.toString(), 0.0),
                                        new PluginCardEntry(MONDAY.toString(), "DINNER", SPAGHETTI.toString(), 100.5),
                                        // Malformed date.
                                        new PluginCardEntry("not-a-date", "DINNER", SPAGHETTI.toString(), 2.0),
                                        // Everything missing.
                                        new PluginCardEntry(null, null, null, null),
                                        // One valid entry.
                                        new PluginCardEntry(
                                                MONDAY.plusDays(1).toString(), "SNACK", DAHL.toString(), 1.5))),
                        // Only invalid entries: the whole card goes.
                        new PluginCard(
                                "All bad",
                                null,
                                null,
                                List.of(new PluginCardEntry(
                                        MONDAY.toString(),
                                        "DINNER",
                                        UUID.randomUUID().toString(),
                                        2.0)))),
                context());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).entries())
                .extracting(SuggestedEntry::recipeTitle)
                .containsExactly("Red Lentil Dahl");
    }

    @Test
    void textFieldsAreBounded() {
        String longText = "x".repeat(81);
        List<PluginCard> cards = List.of(
                new PluginCard(
                        null,
                        null,
                        null,
                        List.of(new PluginCardEntry(MONDAY.toString(), "DINNER", SPAGHETTI.toString(), 2.0))),
                new PluginCard(
                        "   ",
                        null,
                        null,
                        List.of(new PluginCardEntry(MONDAY.toString(), "DINNER", SPAGHETTI.toString(), 2.0))),
                new PluginCard(
                        longText,
                        null,
                        null,
                        List.of(new PluginCardEntry(MONDAY.toString(), "DINNER", SPAGHETTI.toString(), 2.0))),
                new PluginCard(
                        "Long blurb",
                        "y".repeat(201),
                        null,
                        List.of(new PluginCardEntry(MONDAY.toString(), "DINNER", SPAGHETTI.toString(), 2.0))),
                new PluginCard(
                        "Long icon",
                        null,
                        "z".repeat(9),
                        List.of(new PluginCardEntry(MONDAY.toString(), "DINNER", SPAGHETTI.toString(), 2.0))),
                new PluginCard(
                        "Good",
                        "ok blurb",
                        "IT",
                        List.of(new PluginCardEntry(MONDAY.toString(), "DINNER", SPAGHETTI.toString(), 2.0))));
        List<PlanSuggestion> result = SuggestionService.validatedCards(MANIFEST, cards, context());
        assertThat(result).extracting(PlanSuggestion::title).containsExactly("Good");
    }

    @Test
    void countsAreCapped() {
        List<PluginCardEntry> eightEntries = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            eightEntries.add(
                    new PluginCardEntry(MONDAY.plusDays(i % 7).toString(), "DINNER", SPAGHETTI.toString(), 2.0));
        }
        List<PluginCard> sixCards = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sixCards.add(new PluginCard("Card " + i, null, null, eightEntries));
        }
        List<PlanSuggestion> result = SuggestionService.validatedCards(MANIFEST, sixCards, context());
        assertThat(result).hasSize(5);
        assertThat(result.get(0).entries()).hasSize(7);
    }
}
