package io.github.noahhhx.mimos.api.plugins;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.plugins.PanelAction;
import io.github.noahhhx.mimos.plugins.PanelBlock;
import io.github.noahhhx.mimos.plugins.PanelButton;
import io.github.noahhhx.mimos.plugins.PanelSummary;
import io.github.noahhhx.mimos.plugins.PlanSuggestion;
import io.github.noahhhx.mimos.plugins.SuggestedEntry;
import io.github.noahhhx.mimos.plugins.SuggestionService;
import io.github.noahhhx.mimos.plugins.WeekPanel;
import io.github.noahhhx.mimos.plugins.WeekPanelService;
import io.github.noahhhx.mimos.plugins.WheelSegment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.PluginsApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * What plugins contribute to a planned week: suggestion cards (ADR-0006)
 * and week panels (ADR-0017). Cards are advisory and attributed; applying
 * them reuses the existing plan-entry endpoint. A panel's buttons reach
 * only the plugin that drew them, which has no write path into Mimos.
 */
@RestController
public class PluginsController implements PluginsApi {

    private final SuggestionService suggestionService;
    private final WeekPanelService weekPanelService;
    private final CurrentUserService currentUser;

    public PluginsController(
            SuggestionService suggestionService, WeekPanelService weekPanelService, CurrentUserService currentUser) {
        this.suggestionService = suggestionService;
        this.weekPanelService = weekPanelService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<org.openapitools.model.PlanSuggestions> getPlanSuggestions(LocalDate startDate) {
        UUID householdId = currentUser.requireProfile().householdId();
        List<PlanSuggestion> cards = suggestionService.suggestions(householdId, startDate);
        return ResponseEntity.ok(new org.openapitools.model.PlanSuggestions()
                .suggestions(
                        cards.stream().map(PluginsController::toApiSuggestion).toList()));
    }

    @Override
    public ResponseEntity<org.openapitools.model.WeekPanels> getWeekPanels(LocalDate startDate) {
        UUID householdId = currentUser.requireProfile().householdId();
        return ResponseEntity.ok(new org.openapitools.model.WeekPanels()
                .panels(weekPanelService.panels(householdId, startDate).stream()
                        .map(PluginsController::toApiPanel)
                        .toList()));
    }

    @Override
    public ResponseEntity<org.openapitools.model.WeekPanel> pressWeekPanelAction(
            LocalDate startDate, String pluginId, org.openapitools.model.PanelAction panelAction) {
        // The generated model types a required id as non-null but leaves an
        // absent one null; that is a bad request, not a crash.
        @Nullable String id = panelAction.getId();
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        PanelAction action = new PanelAction(id, panelAction.getValue());
        UUID householdId = currentUser.requireProfile().householdId();
        return ResponseEntity.ok(toApiPanel(weekPanelService.act(householdId, startDate, pluginId, action)));
    }

    static org.openapitools.model.PlanSuggestion toApiSuggestion(PlanSuggestion card) {
        return new org.openapitools.model.PlanSuggestion()
                .pluginId(card.pluginId())
                .pluginName(card.pluginName())
                .title(card.title())
                .blurb(card.blurb())
                .icon(card.icon())
                .entries(card.entries().stream()
                        .map(PluginsController::toApiEntry)
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

    static org.openapitools.model.WeekPanel toApiPanel(WeekPanel panel) {
        PanelSummary summary = panel.summary();
        return new org.openapitools.model.WeekPanel()
                .pluginId(panel.pluginId())
                .pluginName(panel.pluginName())
                .summary(
                        summary == null
                                ? null
                                : new org.openapitools.model.PanelSummary()
                                        .icon(summary.icon())
                                        .label(summary.label()))
                .blocks(panel.blocks().stream()
                        .map(PluginsController::toApiBlock)
                        .toList());
    }

    static org.openapitools.model.PanelBlock toApiBlock(PanelBlock block) {
        return switch (block) {
            case PanelBlock.Text text ->
                new org.openapitools.model.PanelTextBlock().type("text").text(text.text());
            case PanelBlock.Highlight highlight ->
                new org.openapitools.model.PanelHighlightBlock()
                        .type("highlight")
                        .icon(highlight.icon())
                        .title(highlight.title())
                        .text(highlight.text());
            case PanelBlock.Wheel wheel ->
                new org.openapitools.model.PanelWheelBlock()
                        .type("wheel")
                        .segments(wheel.segments().stream()
                                .map(PluginsController::toApiSegment)
                                .toList())
                        .landing(wheel.landing());
            case PanelBlock.Actions actions ->
                new org.openapitools.model.PanelActionsBlock()
                        .type("actions")
                        .actions(actions.buttons().stream()
                                .map(PluginsController::toApiButton)
                                .toList());
        };
    }

    private static org.openapitools.model.PanelWheelSegment toApiSegment(WheelSegment segment) {
        return new org.openapitools.model.PanelWheelSegment()
                .label(segment.label())
                .icon(segment.icon());
    }

    private static org.openapitools.model.PanelButton toApiButton(PanelButton button) {
        return new org.openapitools.model.PanelButton()
                .id(button.id())
                .label(button.label())
                .value(button.value())
                .primary(button.primary());
    }
}
