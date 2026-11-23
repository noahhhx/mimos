package io.github.noahhhx.mimos.api.recipes;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestPictures;
import io.github.noahhhx.mimos.api.support.TestUser;
import io.github.noahhhx.mimos.recipes.recipe.RecipePictureService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Recipe tabs (ADR-0036): a tab holds its picked recipes and every recipe
 * with any of its tags, personal or library; it belongs to the household,
 * while the default tab is each person's own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipeCollectionsEndpointTests extends ApiIntegrationTestSupport {

    private static final String COLLECTIONS = "/api/v1/recipe-collections";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void aTabHoldsItsPicksAndEveryRecipeWithAnyOfItsTags() {
        TestUser cook = user();
        UUID pickedOnly = tagged(cook, "Picked only");
        UUID taggedOnly = tagged(cook, "Tagged only", "Weeknight");
        UUID both = tagged(cook, "Picked and tagged", "weeknight");
        UUID otherTag = tagged(cook, "Other tag", "Sunday");
        tagged(cook, "Untagged");
        JsonNode library = cook.ok(HttpMethod.GET, "/api/v1/recipes/library", null);
        List<String> curries = new ArrayList<>();
        UUID libraryPick = null;
        for (JsonNode recipe : library) {
            if (hasTag(recipe, "curry")) {
                curries.add(recipe.get("title").asText());
            } else if (libraryPick == null) {
                libraryPick = UUID.fromString(recipe.get("id").asText());
            }
        }
        assertThat(curries).isNotEmpty();
        UUID picked = requireNonNull(libraryPick);

        JsonNode tab = cook.ok(
                HttpMethod.POST,
                COLLECTIONS,
                input("  Weeknights  ", List.of(" WEEKNIGHT ", "Curry", "weeknight"), pickedOnly, both, picked));

        assertThat(tab.get("name").asText()).isEqualTo("Weeknights");
        assertThat(strings(tab.get("tags"))).containsExactly("curry", "weeknight");
        assertThat(strings(tab.get("recipeIds")))
                .containsExactlyInAnyOrder(pickedOnly.toString(), both.toString(), picked.toString());
        assertThat(tab.get("isDefault").asBoolean()).isFalse();
        assertThat(tab.has("pictureId")).isFalse();
        JsonNode members = cook.ok(HttpMethod.GET, recipesUri(tab), null);
        String pickedTitle = cook.ok(HttpMethod.GET, "/api/v1/recipes/" + picked, null)
                .get("title")
                .asText();
        List<String> libraryTitles = new ArrayList<>(curries);
        libraryTitles.add(pickedTitle);
        libraryTitles.sort(String::compareTo);
        List<String> expected = new ArrayList<>(List.of("Picked and tagged", "Tagged only", "Picked only"));
        expected.addAll(libraryTitles);
        assertThat(titles(members)).containsExactlyElementsOf(expected);
        assertThat(tab.get("recipeCount").asInt()).isEqualTo(expected.size());
        assertThat(ids(members)).doesNotContain(otherTag.toString());
        assertThat(members.get(0).get("createdBy").get("you").asBoolean()).isTrue();

        assertThat(titles(cook.ok(HttpMethod.GET, recipesUri(tab) + "?q=picked", null)))
                .containsExactly("Picked and tagged", "Picked only");

        assertThat(cook.status(HttpMethod.DELETE, "/api/v1/recipes/" + pickedOnly, null))
                .isEqualTo(204);
        JsonNode after = cook.ok(HttpMethod.GET, uri(tab), null);
        assertThat(strings(after.get("recipeIds"))).doesNotContain(pickedOnly.toString());
        assertThat(after.get("recipeCount").asInt()).isEqualTo(expected.size() - 1);
    }

    @Test
    void aTabMayStartEmptyAndIsReplacedKeepingItsPictureAndDefault() {
        TestUser cook = user();
        UUID soup = tagged(cook, "Soup");
        JsonNode tab = cook.ok(HttpMethod.POST, COLLECTIONS, input("Later", List.of()));
        assertThat(tab.get("recipeCount").asInt()).isZero();
        assertThat(cook.ok(HttpMethod.GET, recipesUri(tab), null)).isEmpty();
        String pictureId =
                putPicture(cook, tab, TestPictures.PNG).body().get("pictureId").asText();
        assertThat(cook.status(HttpMethod.PUT, defaultUri(tab), null)).isEqualTo(204);

        JsonNode replaced = cook.ok(HttpMethod.PUT, uri(tab), input("Soups", List.of(), soup));

        assertThat(replaced.get("name").asText()).isEqualTo("Soups");
        assertThat(replaced.get("recipeCount").asInt()).isEqualTo(1);
        assertThat(replaced.get("pictureId").asText()).isEqualTo(pictureId);
        assertThat(replaced.get("isDefault").asBoolean()).isTrue();
        assertThat(replaced.get("createdAt").asText())
                .isEqualTo(tab.get("createdAt").asText());
    }

    @Test
    void tabsAreListedOldestFirstAndNamesAreUniqueIgnoringCase() {
        TestUser cook = user();
        cook.ok(HttpMethod.POST, COLLECTIONS, input("Breakfasts", List.of("breakfast")));
        JsonNode second = cook.ok(HttpMethod.POST, COLLECTIONS, input("Dinners", List.of("dinner")));

        assertThat(cook.status(HttpMethod.POST, COLLECTIONS, input(" breakfasts ", List.of())))
                .isEqualTo(409);
        assertThat(cook.status(HttpMethod.PUT, uri(second), input("BREAKFASTS", List.of())))
                .isEqualTo(409);
        assertThat(cook.status(HttpMethod.PUT, uri(second), input("dinners", List.of())))
                .isEqualTo(200);
        assertThat(names(cook.ok(HttpMethod.GET, COLLECTIONS, null))).containsExactly("Breakfasts", "dinners");
        assertThat(user().status(HttpMethod.POST, COLLECTIONS, input("Breakfasts", List.of())))
                .isEqualTo(201);
    }

    @Test
    void invalidTabsAreRefused() {
        TestUser cook = user();
        TestUser stranger = user();
        UUID theirs = tagged(stranger, "Not yours");

        assertThat(cook.status(HttpMethod.POST, COLLECTIONS, input("   ", List.of())))
                .isEqualTo(400);
        assertThat(cook.status(HttpMethod.POST, COLLECTIONS, input("x".repeat(61), List.of())))
                .isEqualTo(400);
        assertThat(cook.status(HttpMethod.POST, COLLECTIONS, input("Blank tag", List.of(" "))))
                .isEqualTo(400);
        TestUser.Response foreign = cook.send(HttpMethod.POST, COLLECTIONS, input("Foreign", List.of(), theirs));
        assertThat(foreign.status()).isEqualTo(400);
        assertThat(requireNonNull(foreign.body()).get("detail").asText()).contains(theirs.toString());
        assertThat(cook.status(HttpMethod.POST, COLLECTIONS, input("Unknown", List.of(), UUID.randomUUID())))
                .isEqualTo(400);
        assertThat(cook.ok(HttpMethod.GET, COLLECTIONS, null)).isEmpty();
    }

    @Test
    void anotherHouseholdsTabIsNotFound() {
        TestUser owner = user();
        TestUser stranger = user();
        JsonNode tab = owner.ok(HttpMethod.POST, COLLECTIONS, input("Private", List.of("test")));
        String pictureId =
                putPicture(owner, tab, TestPictures.PNG).body().get("pictureId").asText();

        assertThat(stranger.ok(HttpMethod.GET, COLLECTIONS, null)).isEmpty();
        assertThat(stranger.status(HttpMethod.GET, uri(tab), null)).isEqualTo(404);
        assertThat(stranger.status(HttpMethod.GET, recipesUri(tab), null)).isEqualTo(404);
        assertThat(stranger.status(HttpMethod.PUT, uri(tab), input("Mine", List.of())))
                .isEqualTo(404);
        assertThat(stranger.status(HttpMethod.PUT, defaultUri(tab), null)).isEqualTo(404);
        assertThat(stranger.status(HttpMethod.DELETE, defaultUri(tab), null)).isEqualTo(404);
        assertThat(stranger.status(HttpMethod.GET, pictureUri(tab) + "/" + pictureId, null))
                .isEqualTo(404);
        assertThat(putPicture(stranger, tab, TestPictures.PNG).status()).isEqualTo(404);
        assertThat(stranger.status(HttpMethod.DELETE, pictureUri(tab), null)).isEqualTo(404);
        assertThat(stranger.status(HttpMethod.DELETE, uri(tab), null)).isEqualTo(404);
        assertThat(owner.ok(HttpMethod.GET, uri(tab), null).get("pictureId").asText())
                .isEqualTo(pictureId);
    }

    @Test
    void theHouseholdSharesTabsButEachPersonHasTheirOwnDefault() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        JsonNode first = host.ok(HttpMethod.POST, COLLECTIONS, input("Host's pick", List.of()));
        JsonNode second = guest.ok(HttpMethod.POST, COLLECTIONS, input("Guest's pick", List.of()));

        assertThat(host.status(HttpMethod.PUT, defaultUri(first), null)).isEqualTo(204);
        assertThat(host.status(HttpMethod.PUT, defaultUri(first), null)).isEqualTo(204);
        assertThat(guest.status(HttpMethod.PUT, defaultUri(second), null)).isEqualTo(204);

        assertThat(defaults(host)).containsExactly("Host's pick");
        assertThat(defaults(guest)).containsExactly("Guest's pick");
        assertThat(guest.ok(HttpMethod.GET, uri(first), null).get("isDefault").asBoolean())
                .isFalse();

        assertThat(host.status(HttpMethod.PUT, defaultUri(second), null)).isEqualTo(204);
        assertThat(defaults(host)).containsExactly("Guest's pick");
        assertThat(host.status(HttpMethod.DELETE, defaultUri(first), null)).isEqualTo(204);
        assertThat(defaults(host)).containsExactly("Guest's pick");
        assertThat(host.status(HttpMethod.DELETE, defaultUri(second), null)).isEqualTo(204);
        assertThat(defaults(host)).isEmpty();
        assertThat(defaults(guest)).containsExactly("Guest's pick");
    }

    @Test
    void deletingATabClearsEveryonesDefaultOnIt() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        UUID soup = tagged(host, "Kept soup");
        JsonNode tab = host.ok(HttpMethod.POST, COLLECTIONS, input("Going", List.of(), soup));
        host.ok(HttpMethod.POST, COLLECTIONS, input("Staying", List.of()));
        host.status(HttpMethod.PUT, defaultUri(tab), null);
        guest.status(HttpMethod.PUT, defaultUri(tab), null);

        assertThat(guest.status(HttpMethod.DELETE, uri(tab), null)).isEqualTo(204);

        assertThat(defaults(host)).isEmpty();
        assertThat(defaults(guest)).isEmpty();
        assertThat(names(host.ok(HttpMethod.GET, COLLECTIONS, null))).containsExactly("Staying");
        assertThat(host.status(HttpMethod.GET, uri(tab), null)).isEqualTo(404);
        assertThat(host.status(HttpMethod.GET, "/api/v1/recipes/" + soup, null)).isEqualTo(200);
    }

    @Test
    void aDefaultLeftInAHouseholdOneLeftIsIgnored() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        JsonNode tab = host.ok(HttpMethod.POST, COLLECTIONS, input("Shared", List.of()));
        guest.status(HttpMethod.PUT, defaultUri(tab), null);

        guest.ok(HttpMethod.POST, "/api/v1/household/leave", null);

        assertThat(guest.ok(HttpMethod.GET, COLLECTIONS, null)).isEmpty();
        guest.ok(HttpMethod.POST, COLLECTIONS, input("Own", List.of()));
        assertThat(defaults(guest)).isEmpty();
        assertThat(names(host.ok(HttpMethod.GET, COLLECTIONS, null))).containsExactly("Shared");
    }

    @Test
    void joiningFromAHouseholdOfOneLeavesItsTabsBehind() {
        TestUser host = user();
        TestUser joiner = user();
        host.ok(HttpMethod.POST, COLLECTIONS, input("Host tab", List.of()));
        JsonNode own = joiner.ok(HttpMethod.POST, COLLECTIONS, input("Joiner tab", List.of()));
        joiner.status(HttpMethod.PUT, defaultUri(own), null);

        joiner.join(host);

        assertThat(names(joiner.ok(HttpMethod.GET, COLLECTIONS, null))).containsExactly("Host tab");
        assertThat(defaults(joiner)).isEmpty();
    }

    @Test
    void aPictureIsReplacedServedAndRemoved() {
        TestUser cook = user();
        JsonNode tab = cook.ok(HttpMethod.POST, COLLECTIONS, input("Pictured", List.of()));
        byte[] jpeg = TestPictures.jpeg();

        Upload first = putPicture(cook, tab, TestPictures.PNG);
        assertThat(first.status()).isEqualTo(200);
        String firstId = first.body().get("pictureId").asText();
        Upload second = putPicture(cook, tab, jpeg);
        String secondId = second.body().get("pictureId").asText();
        assertThat(secondId).isNotEqualTo(firstId);

        assertThat(cook.status(HttpMethod.GET, pictureUri(tab) + "/" + firstId, null))
                .isEqualTo(404);
        byte[] served = requireNonNull(cook.api
                .get()
                .uri(pictureUri(tab) + "/" + secondId)
                .headers(headers -> headers.setBearerAuth(cook.token))
                .exchange((request, response) -> {
                    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
                    assertThat(response.getHeaders().getCacheControl()).contains("immutable");
                    return response.getBody().readAllBytes();
                }));
        assertThat(served).isEqualTo(jpeg);

        assertThat(cook.status(HttpMethod.DELETE, pictureUri(tab), null)).isEqualTo(204);
        assertThat(cook.ok(HttpMethod.GET, uri(tab), null).has("pictureId")).isFalse();
        assertThat(cook.status(HttpMethod.GET, pictureUri(tab) + "/" + secondId, null))
                .isEqualTo(404);
        assertThat(cook.status(HttpMethod.DELETE, pictureUri(tab), null)).isEqualTo(204);
    }

    @Test
    void aPictureFollowsTheRecipePictureRules() {
        TestUser cook = user();
        JsonNode tab = cook.ok(HttpMethod.POST, COLLECTIONS, input("Strict cover", List.of()));

        Upload notAnImage = putPicture(cook, tab, "not an image".getBytes(StandardCharsets.UTF_8));
        assertThat(notAnImage.status()).isEqualTo(400);
        assertThat(notAnImage.body().get("detail").asText()).contains("JPEG, PNG, or WebP");
        byte[] tooBig = Arrays.copyOf(TestPictures.PNG, RecipePictureService.MAX_PICTURE_BYTES + 1);
        assertThat(putPicture(cook, tab, tooBig).status()).isEqualTo(413);
        byte[] largest = Arrays.copyOf(TestPictures.PNG, RecipePictureService.MAX_PICTURE_BYTES);
        assertThat(putPicture(cook, tab, largest).status()).isEqualTo(200);
    }

    private TestUser user() {
        return new TestUser(port, objectMapper, jdbc);
    }

    /** A personal recipe with exactly these tags. */
    private UUID tagged(TestUser cook, String title, String... tags) {
        UUID id = cook.recipe(title);
        ObjectNode detail = (ObjectNode) cook.ok(HttpMethod.GET, "/api/v1/recipes/" + id, null);
        ArrayNode tagArray = detail.putArray("tags");
        Arrays.stream(tags).forEach(tagArray::add);
        cook.ok(HttpMethod.PUT, "/api/v1/recipes/" + id, detail);
        return id;
    }

    private static Map<String, Object> input(String name, List<String> tags, UUID... recipeIds) {
        return Map.of(
                "name", name,
                "tags", tags,
                "recipeIds", Arrays.stream(recipeIds).map(UUID::toString).toList());
    }

    private Upload putPicture(TestUser cook, JsonNode tab, byte[] bytes) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return "cover";
                    }
                })
                .contentType(MediaType.IMAGE_PNG);
        return requireNonNull(cook.api
                .put()
                .uri(pictureUri(tab))
                .headers(headers -> headers.setBearerAuth(cook.token))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .exchange((request, response) -> new Upload(
                        response.getStatusCode().value(),
                        objectMapper.readTree(response.getBody().readAllBytes()))));
    }

    private record Upload(int status, JsonNode body) {}

    private static List<String> defaults(TestUser person) {
        List<String> names = new ArrayList<>();
        for (JsonNode tab : person.ok(HttpMethod.GET, COLLECTIONS, null)) {
            if (tab.get("isDefault").asBoolean()) {
                names.add(tab.get("name").asText());
            }
        }
        return names;
    }

    private static boolean hasTag(JsonNode recipe, String tag) {
        for (JsonNode each : recipe.get("tags")) {
            if (each.asText().equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static List<String> field(JsonNode array, String name) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.get(name).asText()));
        return values;
    }

    private static List<String> titles(JsonNode recipes) {
        return field(recipes, "title");
    }

    private static List<String> ids(JsonNode recipes) {
        return field(recipes, "id");
    }

    private static List<String> names(JsonNode tabs) {
        return field(tabs, "name");
    }

    private static String uri(JsonNode tab) {
        return COLLECTIONS + "/" + tab.get("id").asText();
    }

    private static String recipesUri(JsonNode tab) {
        return uri(tab) + "/recipes";
    }

    private static String defaultUri(JsonNode tab) {
        return uri(tab) + "/default";
    }

    private static String pictureUri(JsonNode tab) {
        return uri(tab) + "/picture";
    }
}
