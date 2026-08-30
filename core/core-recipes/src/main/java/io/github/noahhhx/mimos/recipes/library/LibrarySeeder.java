package io.github.noahhhx.mimos.recipes.library;

import io.github.noahhhx.mimos.recipes.recipe.Ingredient;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeRepository;
import io.github.noahhhx.mimos.recipes.recipe.RecipeStep;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the curated library from {@code library-seed.json} at startup.
 * Idempotent: recipes missing by slug are inserted, existing library
 * recipes are refreshed with the file's content (so fixing a typo ships
 * with a restart), and slugs claimed by personal recipes are left alone.
 * Disable with {@code mimos.library.seed-enabled=false} (self-hosters who
 * want an empty library or their own curation).
 */
@Component
@ConditionalOnProperty(name = "mimos.library.seed-enabled", havingValue = "true", matchIfMissing = true)
public class LibrarySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LibrarySeeder.class);

    static final String SEED_RESOURCE = "library/library-seed.json";

    private final RecipeRepository repository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public LibrarySeeder(RecipeRepository repository, Clock clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<LibrarySeed> seeds = readSeeds();
        int inserted = 0;
        int refreshed = 0;
        int skipped = 0;
        for (LibrarySeed seed : seeds) {
            switch (upsert(seed)) {
                case INSERTED -> inserted++;
                case REFRESHED -> refreshed++;
                case SKIPPED -> skipped++;
            }
        }
        log.info(
                "Library seed: {} recipes ({} inserted, {} refreshed, {} skipped)",
                seeds.size(),
                inserted,
                refreshed,
                skipped);
    }

    private Outcome upsert(LibrarySeed seed) {
        return repository
                .findBySlug(seed.slug())
                .map(existing -> {
                    if (existing.isLibrary()) {
                        repository.replace(toRecipe(existing.id(), existing.createdAt(), seed));
                        return Outcome.REFRESHED;
                    }
                    log.warn("Seed slug {} is claimed by a personal recipe; skipping", seed.slug());
                    return Outcome.SKIPPED;
                })
                .orElseGet(() -> {
                    repository.insert(toRecipe(UUID.randomUUID(), clock.instant(), seed));
                    return Outcome.INSERTED;
                });
    }

    private Recipe toRecipe(UUID id, java.time.Instant createdAt, LibrarySeed seed) {
        return new Recipe(
                id,
                null,
                seed.slug(),
                seed.title(),
                seed.description(),
                seed.servings(),
                seed.prepMinutes(),
                seed.cookMinutes(),
                seed.nutrition(),
                seed.tags(),
                seed.ingredients().stream()
                        .map(ingredient -> new Ingredient(ingredient.quantity(), ingredient.unit(), ingredient.name()))
                        .toList(),
                seed.steps().stream().map(RecipeStep::new).toList(),
                createdAt,
                clock.instant());
    }

    private List<LibrarySeed> readSeeds() {
        try {
            LibrarySeed[] seeds =
                    objectMapper.readValue(new ClassPathResource(SEED_RESOURCE).getInputStream(), LibrarySeed[].class);
            return List.of(seeds);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read " + SEED_RESOURCE, exception);
        }
    }

    private enum Outcome {
        INSERTED,
        REFRESHED,
        SKIPPED
    }
}
