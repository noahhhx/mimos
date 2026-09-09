package io.github.noahhhx.mimos.api.plugins;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.plugins.PlanSuggestion;
import io.github.noahhhx.mimos.plugins.SuggestedEntry;
import io.github.noahhhx.mimos.plugins.SuggestionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.openapitools.api.PluginsApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan suggestions over the plugin runtime (ADR-0006). Cards are advisory
 * and attributed; applying them reuses the existing plan-entry endpoint —
 * there is no plugin-triggered write path.
 */
@RestController
public class SuggestionsController implements PluginsApi {

    private final SuggestionService suggestionService;
    private final CurrentUserService currentUser;

    public SuggestionsController(SuggestionService suggestionService, CurrentUserService currentUser) {
        this.suggestionService = suggestionService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<org.openapitools.model.PlanSuggestions> getPlanSuggestions(LocalDate startDate) {
        UUID profileId = currentUser.requireProfile().id();
        List<PlanSuggestion> cards = suggestionService.suggestions(profileId, startDate);
        return ResponseEntity.ok(new org.openapitools.model.PlanSuggestions()
                .suggestions(cards.stream()
                        .map(SuggestionsController::toApiSuggestion)
                        .toList()));
    }

    static org.openapitools.model.PlanSuggestion toApiSuggestion(PlanSuggestion card) {
        return new org.openapitools.model.PlanSuggestion()
                .pluginId(card.pluginId())
                .pluginName(card.pluginName())
                .title(card.title())
                .blurb(card.blurb())
                .icon(card.icon())
                .entries(card.entries().stream()
                        .map(SuggestionsController::toApiEntry)
                        .toList());
    }

    static org.openapitools.model.PlanSuggestionEntry toApiEntry(SuggestedEntry entry) {
        return new org.openapitools.model.PlanSuggestionEntry()
                .date(entry.date())
                .mealType(
                        org.openapitools.model.MealType.valueOf(entry.mealType().name()))
                .recipeId(entry.recipeId())
                .recipeTitle(entry.recipeTitle())
                .servings(BigDecimal.valueOf(entry.servings()));
    }
}
