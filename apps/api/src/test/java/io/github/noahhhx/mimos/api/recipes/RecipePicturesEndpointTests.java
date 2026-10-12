package io.github.noahhhx.mimos.api.recipes;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestPictures;
import io.github.noahhhx.mimos.api.support.TestUser;
import io.github.noahhhx.mimos.recipes.recipe.RecipePictureService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Recipe pictures end to end (ADR-0023): uploads are checked by their bytes,
 * served with year-long private caching, listed on the recipe in upload
 * order, kept by a replace, and visible only to the owning household.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipePicturesEndpointTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void servesUploadedPicturesInOrderAndReplaceKeepsThem() {
        TestUser cook = user();
        UUID recipe = cook.recipe("Pictured stew");
        byte[] jpeg = TestPictures.jpeg();

        UUID first = TestPictures.add(cook.api, cook.token, recipe, TestPictures.PNG);
        UUID second = TestPictures.add(cook.api, cook.token, recipe, jpeg);

        Image png = image(cook, recipe, first);
        assertThat(png.status()).isEqualTo(200);
        assertThat(png.bytes()).isEqualTo(TestPictures.PNG);
        assertThat(png.headers().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(png.headers().getCacheControl()).isEqualTo("max-age=31536000, private, immutable");
        assertThat(png.headers().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        Image served = image(cook, recipe, second);
        assertThat(served.bytes()).isEqualTo(jpeg);
        assertThat(served.headers().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);

        JsonNode detail = cook.ok(HttpMethod.GET, "/api/v1/recipes/" + recipe, null);
        assertThat(pictureIds(detail)).containsExactly(first, second);

        ObjectNode input = (ObjectNode) detail.deepCopy();
        input.put("title", "Pictured stew, renamed");
        JsonNode replaced = cook.ok(HttpMethod.PUT, "/api/v1/recipes/" + recipe, input);
        assertThat(replaced.get("title").asText()).isEqualTo("Pictured stew, renamed");
        assertThat(pictureIds(replaced)).containsExactly(first, second);
    }

    @Test
    void removingAPictureKeepsTheOthersInOrder() {
        TestUser cook = user();
        UUID recipe = cook.recipe("Three pictures");
        UUID first = TestPictures.add(cook.api, cook.token, recipe, TestPictures.PNG);
        UUID second = TestPictures.add(cook.api, cook.token, recipe, TestPictures.PNG);
        UUID third = TestPictures.add(cook.api, cook.token, recipe, TestPictures.PNG);

        assertThat(cook.status(HttpMethod.DELETE, pictureUri(recipe, second), null))
                .isEqualTo(204);
        assertThat(image(cook, recipe, second).status()).isEqualTo(404);
        assertThat(cook.status(HttpMethod.DELETE, pictureUri(recipe, second), null))
                .isEqualTo(404);
        UUID fourth = TestPictures.add(cook.api, cook.token, recipe, TestPictures.PNG);
        assertThat(pictureIds(cook.ok(HttpMethod.GET, "/api/v1/recipes/" + recipe, null)))
                .containsExactly(first, third, fourth);
    }

    @Test
    void rejectsWhatIsNotAPictureAndMoreThanTheLimit() {
        TestUser cook = user();
        UUID recipe = cook.recipe("Picky");
        byte[] gif = "GIF89a\u0001\0\u0001\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        TestPictures.Upload disguised = TestPictures.upload(cook.api, cook.token, recipe, gif, MediaType.IMAGE_PNG);
        assertThat(disguised.status()).isEqualTo(400);
        assertThat(disguised.body()).contains("a picture must be a JPEG, PNG, or WebP image");
        assertThat(TestPictures.upload(cook.api, cook.token, recipe, new byte[0], MediaType.IMAGE_PNG)
                        .status())
                .isEqualTo(400);

        byte[] tooBig = Arrays.copyOf(TestPictures.PNG, RecipePictureService.MAX_PICTURE_BYTES + 1);
        TestPictures.Upload oversized = TestPictures.upload(cook.api, cook.token, recipe, tooBig, MediaType.IMAGE_PNG);
        assertThat(oversized.status()).isEqualTo(413);
        assertThat(oversized.body()).contains("\"status\":413");
        byte[] largest = Arrays.copyOf(TestPictures.PNG, RecipePictureService.MAX_PICTURE_BYTES);
        TestPictures.add(cook.api, cook.token, recipe, largest);

        for (int i = 1; i < RecipePictureService.MAX_PICTURES; i++) {
            TestPictures.add(cook.api, cook.token, recipe, TestPictures.PNG);
        }
        TestPictures.Upload ninth =
                TestPictures.upload(cook.api, cook.token, recipe, TestPictures.PNG, MediaType.IMAGE_PNG);
        assertThat(ninth.status()).isEqualTo(400);
        assertThat(ninth.body()).contains("a recipe can have at most 8 pictures");
    }

    @Test
    void anotherHouseholdSeesNothingAndLibraryRecipesAreReadOnly() {
        TestUser cook = user();
        TestUser stranger = user();
        UUID recipe = cook.recipe("Private picture");
        UUID picture = TestPictures.add(cook.api, cook.token, recipe, TestPictures.PNG);

        assertThat(image(stranger, recipe, picture).status()).isEqualTo(404);
        assertThat(TestPictures.upload(stranger.api, stranger.token, recipe, TestPictures.PNG, MediaType.IMAGE_PNG)
                        .status())
                .isEqualTo(404);
        assertThat(stranger.status(HttpMethod.DELETE, pictureUri(recipe, picture), null))
                .isEqualTo(404);
        assertThat(image(cook, recipe, picture).status()).isEqualTo(200);

        UUID library = requireNonNull(
                jdbc.queryForObject("select id from recipe where household_id is null limit 1", UUID.class));
        assertThat(TestPictures.upload(cook.api, cook.token, library, TestPictures.PNG, MediaType.IMAGE_PNG)
                        .status())
                .isEqualTo(403);
        assertThat(pictureIds(cook.ok(HttpMethod.GET, "/api/v1/recipes/" + library, null)))
                .isEmpty();
    }

    @Test
    void aSecondMemberOfTheHouseholdSeesAndRemovesPictures() {
        TestUser cook = user();
        TestUser partner = user();
        partner.join(cook);
        UUID recipe = cook.recipe("Shared picture");
        UUID picture = TestPictures.add(cook.api, cook.token, recipe, TestPictures.PNG);

        assertThat(image(partner, recipe, picture).bytes()).isEqualTo(TestPictures.PNG);
        assertThat(partner.status(HttpMethod.DELETE, pictureUri(recipe, picture), null))
                .isEqualTo(204);
    }

    @Test
    void deletingTheRecipeDeletesItsPictures() {
        TestUser cook = user();
        UUID recipe = cook.recipe("Short-lived");
        UUID picture = TestPictures.add(cook.api, cook.token, recipe, TestPictures.PNG);

        assertThat(cook.status(HttpMethod.DELETE, "/api/v1/recipes/" + recipe, null))
                .isEqualTo(204);
        assertThat(jdbc.queryForObject(
                        "select count(*) from recipe_picture where recipe_id = ?", Integer.class, recipe))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from recipe_picture_data where picture_id = ?", Integer.class, picture))
                .isZero();
    }

    @Test
    void concurrentUploadsNeverPassTheLimitOrShareAPosition() throws Exception {
        TestUser cook = user();
        UUID recipe = cook.recipe("Rush");
        TestPictures.add(cook.api, cook.token, recipe, TestPictures.PNG);

        List<Callable<Integer>> uploads = new ArrayList<>();
        for (int i = 0; i < RecipePictureService.MAX_PICTURES; i++) {
            uploads.add(() -> TestPictures.upload(cook.api, cook.token, recipe, TestPictures.PNG, MediaType.IMAGE_PNG)
                    .status());
        }
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(uploads.size())) {
            for (Future<Integer> status : pool.invokeAll(uploads)) {
                statuses.add(status.get());
            }
        }

        assertThat(statuses.stream().filter(status -> status == 201)).hasSize(RecipePictureService.MAX_PICTURES - 1);
        assertThat(statuses.stream().filter(status -> status == 400)).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "select count(distinct position) from recipe_picture where recipe_id = ?",
                        Integer.class,
                        recipe))
                .isEqualTo(RecipePictureService.MAX_PICTURES);
    }

    private TestUser user() {
        return new TestUser(port, objectMapper, jdbc);
    }

    private static String pictureUri(UUID recipe, UUID picture) {
        return "/api/v1/recipes/" + recipe + "/pictures/" + picture;
    }

    private static List<UUID> pictureIds(JsonNode detail) {
        List<UUID> ids = new ArrayList<>();
        detail.get("pictures")
                .forEach(picture -> ids.add(UUID.fromString(picture.get("id").asText())));
        return ids;
    }

    private static Image image(TestUser user, UUID recipe, UUID picture) {
        return requireNonNull(user.api
                .get()
                .uri(pictureUri(recipe, picture))
                .headers(headers -> headers.setBearerAuth(user.token))
                .exchange((request, response) -> new Image(
                        response.getStatusCode().value(),
                        response.getHeaders(),
                        response.getBody().readAllBytes())));
    }

    private record Image(int status, HttpHeaders headers, byte[] bytes) {}
}
