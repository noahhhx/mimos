package io.github.noahhhx.mimos.recipes.library;

import io.github.noahhhx.mimos.recipes.recipe.CatalogIngredient;
import io.github.noahhhx.mimos.recipes.recipe.IngredientCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the shared ingredient catalog from {@code ingredient-seed.json} at
 * startup (ADR-0015): entries are inserted or refreshed by slug and never
 * deleted, since recipes link to them. Always on, because personal recipes
 * link to the catalog whether or not the library is seeded, and before the
 * library seeder, whose recipes link to it too.
 */
@Component
@Order(0)
public class IngredientCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngredientCatalogSeeder.class);

    static final String SEED_RESOURCE = "library/ingredient-seed.json";

    private final IngredientCatalog catalog;
    private final ObjectMapper objectMapper;

    public IngredientCatalogSeeder(IngredientCatalog catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<CatalogIngredient> entries = readSeeds();
        entries.forEach(catalog::upsert);
        log.info("Ingredient catalog seed: {} ingredients", entries.size());
    }

    private List<CatalogIngredient> readSeeds() {
        try {
            return List.of(objectMapper.readValue(
                    new ClassPathResource(SEED_RESOURCE).getInputStream(), CatalogIngredient[].class));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read " + SEED_RESOURCE, exception);
        }
    }
}
