package io.github.noahhhx.mimos.api.recipes;

import io.github.noahhhx.mimos.api.identity.IdentityService;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.openapitools.model.Person;
import org.openapitools.model.RecipeSummary;
import org.springframework.stereotype.Component;

@Component
class RecipeSummaries {

    private final IdentityService identity;

    RecipeSummaries(IdentityService identity) {
        this.identity = identity;
    }

    List<RecipeSummary> of(List<Recipe> recipes, UUID callerId) {
        Map<UUID, String> names = identity.displayNames(recipes.stream()
                .map(Recipe::createdByProfileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return recipes.stream()
                .map(recipe -> RecipeApiMapper.toSummary(recipe).createdBy(creator(recipe, names, callerId)))
                .toList();
    }

    static @Nullable Person creator(Recipe recipe, Map<UUID, String> names, UUID callerId) {
        UUID creatorId = recipe.createdByProfileId();
        String name = creatorId == null ? null : names.get(creatorId);
        if (creatorId == null || name == null) {
            return null;
        }
        return new Person().id(creatorId).displayName(name).you(creatorId.equals(callerId));
    }
}
