package io.github.noahhhx.mimos.planning.shopping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Best-effort aisle grouping for shopping-list items, from the ingredient
 * name. Kitchen realism over perfection: unknown names land in "Other".
 * More specific keywords are checked before generic ones ("black pepper"
 * is pantry, "bell pepper" is produce). This is a display concern, not a
 * domain invariant — it lives in code so every instance gets the same
 * behavior without data setup.
 */
public final class Aisles {

    public static final String PRODUCE = "Produce";
    public static final String MEAT_AND_SEAFOOD = "Meat & Seafood";
    public static final String DAIRY_AND_EGGS = "Dairy & Eggs";
    public static final String BAKERY = "Bakery";
    public static final String FROZEN = "Frozen";
    public static final String PANTRY = "Pantry";
    public static final String OTHER = "Other";

    /** Display order of the aisle groups. */
    public static final List<String> CATEGORY_ORDER =
            List.of(PRODUCE, MEAT_AND_SEAFOOD, DAIRY_AND_EGGS, BAKERY, FROZEN, PANTRY, OTHER);

    /** Substring keywords in match order; first hit wins. */
    private static final Map<String, String> KEYWORDS = buildKeywords();

    private Aisles() {}

    /** Guesses the aisle for an ingredient name (case-insensitive substring match). */
    public static String categorize(String ingredientName) {
        String name = ingredientName.toLowerCase(Locale.ROOT);
        if (name.contains("frozen")) {
            return FROZEN;
        }
        for (Map.Entry<String, String> entry : KEYWORDS.entrySet()) {
            if (name.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return OTHER;
    }

    /** Normalizes an ingredient name for aggregation: case and whitespace only. */
    public static String normalizeName(String name) {
        return name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static Map<String, String> buildKeywords() {
        Map<String, String> keywords = new LinkedHashMap<>();
        // Specific before generic (first substring hit wins).
        keywords.put("black pepper", PANTRY);
        keywords.put("pepper flakes", PANTRY);
        keywords.put("cayenne", PANTRY);
        keywords.put("bell pepper", PRODUCE);
        keywords.put("corn starch", PANTRY);
        keywords.put("cornstarch", PANTRY);
        keywords.put("tomato paste", PANTRY);
        keywords.put("tomato sauce", PANTRY);
        keywords.put("canned", PANTRY);
        keywords.put("passata", PANTRY);
        // Meat & seafood.
        keywords.put("chicken", MEAT_AND_SEAFOOD);
        keywords.put("beef", MEAT_AND_SEAFOOD);
        keywords.put("pork", MEAT_AND_SEAFOOD);
        keywords.put("lamb", MEAT_AND_SEAFOOD);
        keywords.put("turkey", MEAT_AND_SEAFOOD);
        keywords.put("steak", MEAT_AND_SEAFOOD);
        keywords.put("mince", MEAT_AND_SEAFOOD);
        keywords.put("sausage", MEAT_AND_SEAFOOD);
        keywords.put("bacon", MEAT_AND_SEAFOOD);
        keywords.put("ham", MEAT_AND_SEAFOOD);
        keywords.put("fish", MEAT_AND_SEAFOOD);
        keywords.put("salmon", MEAT_AND_SEAFOOD);
        keywords.put("tuna", MEAT_AND_SEAFOOD);
        keywords.put("cod", MEAT_AND_SEAFOOD);
        keywords.put("shrimp", MEAT_AND_SEAFOOD);
        keywords.put("prawn", MEAT_AND_SEAFOOD);
        // Dairy & eggs.
        keywords.put("milk", DAIRY_AND_EGGS);
        keywords.put("cheese", DAIRY_AND_EGGS);
        keywords.put("butter", DAIRY_AND_EGGS);
        keywords.put("yogurt", DAIRY_AND_EGGS);
        keywords.put("yoghurt", DAIRY_AND_EGGS);
        keywords.put("cream", DAIRY_AND_EGGS);
        keywords.put("egg", DAIRY_AND_EGGS);
        keywords.put("mozzarella", DAIRY_AND_EGGS);
        keywords.put("parmesan", DAIRY_AND_EGGS);
        keywords.put("cheddar", DAIRY_AND_EGGS);
        keywords.put("feta", DAIRY_AND_EGGS);
        // Bakery.
        keywords.put("bread", BAKERY);
        keywords.put("tortilla", BAKERY);
        keywords.put("bun", BAKERY);
        keywords.put("pita", BAKERY);
        keywords.put("baguette", BAKERY);
        // Pantry.
        keywords.put("rice", PANTRY);
        keywords.put("pasta", PANTRY);
        keywords.put("spaghetti", PANTRY);
        keywords.put("linguine", PANTRY);
        keywords.put("penne", PANTRY);
        keywords.put("fusilli", PANTRY);
        keywords.put("macaroni", PANTRY);
        keywords.put("tagliatelle", PANTRY);
        keywords.put("orzo", PANTRY);
        keywords.put("noodle", PANTRY);
        keywords.put("flour", PANTRY);
        keywords.put("sugar", PANTRY);
        keywords.put("oil", PANTRY);
        keywords.put("salt", PANTRY);
        keywords.put("vinegar", PANTRY);
        keywords.put("soy sauce", PANTRY);
        keywords.put("stock", PANTRY);
        keywords.put("broth", PANTRY);
        keywords.put("honey", PANTRY);
        keywords.put("oats", PANTRY);
        keywords.put("quinoa", PANTRY);
        keywords.put("lentil", PANTRY);
        keywords.put("chickpea", PANTRY);
        keywords.put("bean", PANTRY);
        keywords.put("spice", PANTRY);
        keywords.put("paprika", PANTRY);
        keywords.put("cumin", PANTRY);
        keywords.put("coriander", PANTRY);
        keywords.put("cinnamon", PANTRY);
        keywords.put("chili", PANTRY);
        keywords.put("peanut butter", PANTRY);
        keywords.put("nuts", PANTRY);
        keywords.put("almond", PANTRY);
        keywords.put("sesame", PANTRY);
        keywords.put("maple syrup", PANTRY);
        keywords.put("vanilla", PANTRY);
        keywords.put("baking powder", PANTRY);
        keywords.put("mustard", PANTRY);
        keywords.put("mayonnaise", PANTRY);
        keywords.put("ketchup", PANTRY);
        // Produce.
        keywords.put("lettuce", PRODUCE);
        keywords.put("tomato", PRODUCE);
        keywords.put("onion", PRODUCE);
        keywords.put("garlic", PRODUCE);
        keywords.put("potato", PRODUCE);
        keywords.put("carrot", PRODUCE);
        keywords.put("celery", PRODUCE);
        keywords.put("lemon", PRODUCE);
        keywords.put("lime", PRODUCE);
        keywords.put("apple", PRODUCE);
        keywords.put("banana", PRODUCE);
        keywords.put("spinach", PRODUCE);
        keywords.put("kale", PRODUCE);
        keywords.put("cucumber", PRODUCE);
        keywords.put("zucchini", PRODUCE);
        keywords.put("mushroom", PRODUCE);
        keywords.put("broccoli", PRODUCE);
        keywords.put("cauliflower", PRODUCE);
        keywords.put("avocado", PRODUCE);
        keywords.put("berries", PRODUCE);
        keywords.put("strawberr", PRODUCE);
        keywords.put("blueberr", PRODUCE);
        keywords.put("raspberr", PRODUCE);
        keywords.put("orange", PRODUCE);
        keywords.put("corn", PRODUCE);
        keywords.put("peas", PRODUCE);
        keywords.put("cabbage", PRODUCE);
        keywords.put("ginger", PRODUCE);
        keywords.put("pepper", PRODUCE);
        keywords.put("scallion", PRODUCE);
        keywords.put("shallot", PRODUCE);
        keywords.put("parsley", PRODUCE);
        keywords.put("cilantro", PRODUCE);
        keywords.put("basil", PRODUCE);
        keywords.put("mint", PRODUCE);
        keywords.put("thyme", PRODUCE);
        keywords.put("rosemary", PRODUCE);
        keywords.put("dill", PRODUCE);
        return keywords;
    }
}
