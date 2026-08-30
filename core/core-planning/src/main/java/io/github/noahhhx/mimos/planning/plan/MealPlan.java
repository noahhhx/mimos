package io.github.noahhhx.mimos.planning.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A week of planned meals, keyed by its Monday. */
public record MealPlan(UUID id, UUID ownerProfileId, LocalDate startDate, List<PlannedMeal> entries) {}
