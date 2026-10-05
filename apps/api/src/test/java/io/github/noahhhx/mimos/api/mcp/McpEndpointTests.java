package io.github.noahhhx.mimos.api.mcp;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.RequestLoggingFilter;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Agents driving Mimos through {@code /mcp} with the MCP Java SDK client,
 * over the real HTTP port, with real Keycloak tokens: the tool list follows
 * the contract, tool calls behave like the HTTP API (ownership,
 * problem-details), and an unauthenticated client is pointed at Keycloak.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class McpEndpointTests extends ApiIntegrationTestSupport {

    private static final Set<String> OPTED_OUT =
            Set.of("listPublicRecipes", "getPublicRecipe", "exportAccount", "importAccount");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    String issuer;

    @Value("${spring.ai.mcp.server.instructions}")
    String instructions;

    @Test
    void everyApiOperationIsAToolUnlessItOptsOut() throws Exception {
        Set<String> operations = new HashSet<>();
        Set<String> optedOut = new HashSet<>();
        try (InputStream in = new ClassPathResource(McpServerConfig.SPEC_LOCATION).getInputStream()) {
            JsonNode spec = new YAMLMapper().readTree(in);
            for (Map.Entry<String, JsonNode> path : spec.get("paths").properties()) {
                if (!path.getKey().startsWith("/api/v1/")) {
                    continue;
                }
                for (JsonNode operation : path.getValue()) {
                    if (operation.has("operationId")) {
                        String id = operation.get("operationId").asString();
                        boolean optOut = operation.path("x-mcp").isBoolean()
                                && !operation.get("x-mcp").booleanValue();
                        (optOut ? optedOut : operations).add(id);
                    }
                }
            }
        }

        List<McpSchema.Tool> tools = listTools(accessToken());

        assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrderElementsOf(operations);
        assertThat(optedOut).isEqualTo(OPTED_OUT);
        assertThat(tools).allSatisfy(tool -> assertThat(tool.description()).isNotBlank());
        McpSchema.Tool getMealPlan = tools.stream()
                .filter(tool -> tool.name().equals("getMealPlan"))
                .findFirst()
                .orElseThrow();
        assertThat(getMealPlan.annotations().readOnlyHint()).isTrue();
        assertThat(getMealPlan.inputSchema().get("required")).isEqualTo(List.of("startDate"));
    }

    @Test
    void instructionsNameOnlyRealTools() {
        Set<String> names = new HashSet<>();
        for (McpSchema.Tool tool : listTools(accessToken())) {
            names.add(tool.name());
            if (tool.inputSchema().get("properties") instanceof Map<?, ?> properties) {
                properties.keySet().forEach(parameter -> names.add(String.valueOf(parameter)));
            }
        }
        Matcher toolLike = Pattern.compile("\\b[a-z]+[A-Z][A-Za-z]+\\b").matcher(instructions);
        List<String> mentioned = new ArrayList<>();
        while (toolLike.find()) {
            mentioned.add(toolLike.group());
        }
        assertThat(mentioned)
                .isNotEmpty()
                .allSatisfy(name -> assertThat(names)
                        .as("tool or parameter named in spring.ai.mcp.server.instructions")
                        .contains(name));
    }

    @Test
    void agentCreatesARecipeFindsItByIngredientPlansItAndShopsForIt() {
        String token = accessToken(createUser());
        LocalDate monday = LocalDate.of(2031, 1, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        JsonNode recipe = json(call(token, "createRecipe", Map.of("body", stewInput())));
        String recipeId = recipe.get("id").asString();

        JsonNode found = json(call(token, "listMyRecipes", Map.of("q", "beef")));
        assertThat(found.valueStream().map(r -> r.get("title").asString())).containsExactly("Agent Beef Stew");

        JsonNode entry = json(call(
                token,
                "addMealPlanEntry",
                Map.of(
                        "startDate",
                        monday.toString(),
                        "body",
                        Map.of(
                                "date",
                                monday.plusDays(2).toString(),
                                "mealType",
                                "DINNER",
                                "recipeId",
                                recipeId,
                                "servings",
                                4))));
        assertThat(entry.get("recipeTitle").asString()).isEqualTo("Agent Beef Stew");

        JsonNode plan = json(call(token, "getMealPlan", Map.of("startDate", monday.toString())));
        assertThat(plan.get("entries").valueStream().map(e -> e.get("id").asString()))
                .containsExactly(entry.get("id").asString());

        JsonNode list = json(call(token, "generateShoppingList", Map.of("startDate", monday.toString())));
        JsonNode beef = list.get("items")
                .valueStream()
                .filter(item -> item.get("name").asString().equals("beef chuck"))
                .findFirst()
                .orElseThrow();
        assertThat(beef.get("quantity").asDouble()).isEqualTo(1000);

        json(call(
                token,
                "updateShoppingListItem",
                Map.of(
                        "startDate",
                        monday.toString(),
                        "itemId",
                        beef.get("id").asString(),
                        "body",
                        Map.of("checked", true))));
        JsonNode remaining = json(call(token, "getShoppingList", Map.of("startDate", monday.toString())));
        assertThat(remaining
                        .get("items")
                        .valueStream()
                        .filter(item -> !item.get("checked").booleanValue()))
                .extracting(item -> item.get("name").asString())
                .containsExactly("carrots");

        CallToolResult deleted = call(token, "deleteRecipe", Map.of("recipeId", recipeId));
        assertThat(deleted.isError()).isFalse();
        assertThat(text(deleted)).isEqualTo("Done.");
    }

    @Test
    void apiErrorsComeBackAsToolErrorsWithTheProblemDetail() {
        String token = accessToken();

        CallToolResult notMonday = call(
                token,
                "addMealPlanEntry",
                Map.of(
                        "startDate",
                        "2031-01-08",
                        "body",
                        Map.of(
                                "date",
                                "2031-01-08",
                                "mealType",
                                "DINNER",
                                "recipeId",
                                UUID.randomUUID().toString(),
                                "servings",
                                1)));
        assertThat(notMonday.isError()).isTrue();
        JsonNode problem = json(text(notMonday));
        assertThat(problem.get("status").asInt()).isEqualTo(400);
        assertThat(problem.get("detail").asString()).contains("Monday");

        CallToolResult misplaced = call(token, "createRecipe", Map.of("title", "No body"));
        assertThat(misplaced.isError()).isTrue();
        assertThat(text(misplaced)).contains("body").contains("title");
    }

    @Test
    void anotherUsersRecipeIsNotFoundThroughTheTools() {
        String token = accessToken(createUser());
        String recipeId = json(call(token, "createRecipe", Map.of("body", stewInput())))
                .get("id")
                .asString();

        CallToolResult foreign = call(accessToken("test2"), "getRecipe", Map.of("recipeId", recipeId));

        assertThat(foreign.isError()).isTrue();
        assertThat(json(text(foreign)).get("status").asInt()).isEqualTo(404);
        assertThat(json(call(accessToken("test2"), "listMyRecipes", Map.of("q", "Agent Beef Stew"))))
                .isEmpty();
    }

    @Test
    void theInnerApiRequestSharesTheMcpRequestId(CapturedOutput output) {
        String id = "mcp-" + UUID.randomUUID();

        try (McpSyncClient client = client(accessToken(), id)) {
            client.callTool(new McpSchema.CallToolRequest("listLibraryRecipes", Map.of()));
        }

        List<String> lines =
                output.getOut().lines().filter(line -> line.contains(id)).toList();
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("POST /mcp -> 200"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("GET /api/v1/recipes/library -> 200"));
    }

    @Test
    void anUnauthenticatedClientIsPointedAtKeycloak() {
        RestClient http = RestClient.create("http://localhost:" + port);

        http.post()
                .uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .body("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(401);
                    assertThat(res.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                            .isEqualTo("Bearer resource_metadata=\"http://localhost:" + port
                                    + "/.well-known/oauth-protected-resource/mcp\"");
                    assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                            .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    return null;
                });

        JsonNode metadata = requireNonNull(
                http.get()
                        .uri("/.well-known/oauth-protected-resource/mcp")
                        .retrieve()
                        .body(JsonNode.class),
                "metadata returned no body");
        assertThat(metadata.get("resource").asString()).isEqualTo("http://localhost:" + port + "/mcp");
        assertThat(metadata.get("authorization_servers").valueStream().map(JsonNode::asString))
                .containsExactly(issuer);
        assertThat(metadata.get("bearer_methods_supported").valueStream().map(JsonNode::asString))
                .containsExactly("header");
        assertThat(metadata.get("scopes_supported").valueStream().map(JsonNode::asString))
                .containsExactly("openid");
    }

    @Test
    void metadataUsesTheProxysPublicUrl() {
        JsonNode metadata = requireNonNull(
                RestClient.create("http://localhost:" + port)
                        .get()
                        .uri("/.well-known/oauth-protected-resource/mcp")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "api.mimos.example")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .retrieve()
                        .body(JsonNode.class),
                "metadata returned no body");

        assertThat(metadata.get("resource").asString()).isEqualTo("https://api.mimos.example/mcp");
    }

    private Map<String, Object> stewInput() {
        return Map.of(
                "title",
                "Agent Beef Stew",
                "description",
                "Slow and rich.",
                "servings",
                2,
                "tags",
                List.of("dinner"),
                "nutrition",
                Map.of("calories", 550),
                "ingredients",
                List.of(
                        Map.of("quantity", 500, "unit", "g", "name", "beef chuck"),
                        Map.of("quantity", 3, "name", "carrots")),
                "steps",
                List.of(Map.of("instruction", "Brown the beef."), Map.of("instruction", "Simmer for two hours.")));
    }

    private List<McpSchema.Tool> listTools(String token) {
        try (McpSyncClient client = client(token, null)) {
            return client.listTools().tools();
        }
    }

    private CallToolResult call(String token, String tool, Map<String, Object> arguments) {
        try (McpSyncClient client = client(token, null)) {
            return client.callTool(new McpSchema.CallToolRequest(tool, arguments));
        }
    }

    private McpSyncClient client(String token, @Nullable String requestId) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(
                        "http://localhost:" + port)
                .endpoint("/mcp")
                .httpRequestCustomizer((builder, method, endpoint, body, context) -> {
                    builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    if (requestId != null) {
                        builder.header(RequestLoggingFilter.HEADER, requestId);
                    }
                })
                .build();
        McpSyncClient client = McpClient.sync(transport).build();
        client.initialize();
        return client;
    }

    private static String text(CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    private JsonNode json(CallToolResult result) {
        assertThat(result.isError()).as(text(result)).isFalse();
        return json(text(result));
    }

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }
}
