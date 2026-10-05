package io.github.noahhhx.mimos.api.mcp;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

class OpenApiToolParserTest {

    private static final String SPEC = """
            openapi: 3.1.0
            paths:
              /api/v1/things/{thingId}:
                parameters:
                  - $ref: '#/components/parameters/ThingId'
                get:
                  operationId: getThing
                  summary: A thing
                  description: >
                    Folded text.
                  parameters:
                    - name: expand
                      in: query
                      description: Include parts.
                      schema:
                        type: boolean
                    - name: lang
                      in: query
                      required: true
                      schema:
                        type: string
                put:
                  operationId: replaceThing
                  summary: Replace a thing
                  requestBody:
                    required: true
                    description: The whole thing.
                    content:
                      application/json:
                        schema:
                          $ref: '#/components/schemas/Thing'
                patch:
                  operationId: patchThing
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                delete:
                  operationId: deleteThing
                  summary: Delete a thing
              /api/v1/things:
                post:
                  operationId: createThing
                  summary: Create a thing
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          $ref: '#/components/schemas/Thing'
                get:
                  operationId: exportThings
                  x-mcp: false
              /actuator/things:
                get:
                  operationId: internalThing
            components:
              parameters:
                ThingId:
                  name: thingId
                  in: path
                  required: true
                  description: The thing's id.
                  schema:
                    type: string
                    format: uuid
              schemas:
                Thing:
                  type: object
                  required: [name]
                  properties:
                    name:
                      type: string
                    part:
                      $ref: '#/components/schemas/Part'
                Part:
                  type: object
                  properties:
                    kind:
                      $ref: '#/components/schemas/Kind'
                    parent:
                      $ref: '#/components/schemas/Part'
                Kind:
                  type: string
                  enum: [A, B]
                Unused:
                  type: string
            """;

    private final Map<String, ToolOperation> tools = parse(SPEC);

    @Test
    void everyApiOperationIsAToolUnlessItOptsOut() {
        assertThat(tools.keySet())
                .containsExactlyInAnyOrder("getThing", "replaceThing", "patchThing", "deleteThing", "createThing");
    }

    @Test
    void parametersBecomePropertiesWithRefsResolved() {
        ToolOperation getThing = tool("getThing");
        assertThat(getThing.method()).isEqualTo(HttpMethod.GET);
        assertThat(getThing.pathTemplate()).isEqualTo("/api/v1/things/{thingId}");
        assertThat(getThing.pathParams()).containsExactly("thingId");
        assertThat(getThing.queryParams()).containsExactly("expand", "lang");
        assertThat(getThing.hasBody()).isFalse();
        assertThat(getThing.description()).isEqualTo("A thing\n\nFolded text.");

        JsonNode schema = getThing.inputSchema();
        assertThat(schema.get("type").asString()).isEqualTo("object");
        assertThat(schema.get("additionalProperties").booleanValue()).isFalse();
        assertThat(schema.at("/properties/thingId/format").asString()).isEqualTo("uuid");
        assertThat(schema.at("/properties/thingId/description").asString()).isEqualTo("The thing's id.");
        assertThat(schema.at("/properties/expand/type").asString()).isEqualTo("boolean");
        assertThat(texts(schema.get("required"))).containsExactly("thingId", "lang");
        assertThat(schema.has("$defs")).isFalse();
    }

    @Test
    void bodySchemaRefsAreCopiedIntoDefsTransitively() {
        ToolOperation replace = tool("replaceThing");
        assertThat(replace.hasBody()).isTrue();
        JsonNode schema = replace.inputSchema();
        assertThat(schema.at("/properties/body/$ref").asString()).isEqualTo("#/$defs/Thing");
        assertThat(schema.at("/properties/body/description").asString()).isEqualTo("The whole thing.");
        assertThat(texts(schema.get("required"))).containsExactly("thingId", "body");
        assertThat(schema.get("$defs").propertyNames()).containsExactlyInAnyOrder("Thing", "Part", "Kind");
        assertThat(schema.at("/$defs/Thing/properties/part/$ref").asString()).isEqualTo("#/$defs/Part");
        assertThat(schema.at("/$defs/Part/properties/kind/$ref").asString()).isEqualTo("#/$defs/Kind");
        assertThat(schema.at("/$defs/Part/properties/parent/$ref").asString()).isEqualTo("#/$defs/Part");
        assertThat(schema.toString()).doesNotContain("#/components/");
    }

    @Test
    void optionalBodyIsNotRequired() {
        JsonNode schema = tool("patchThing").inputSchema();
        assertThat(schema.at("/properties/body/type").asString()).isEqualTo("object");
        assertThat(texts(schema.get("required"))).containsExactly("thingId");
    }

    @Test
    void hintsFollowTheHttpMethod() {
        assertThat(hints(tool("getThing"))).containsExactly(true, false, true);
        assertThat(hints(tool("createThing"))).containsExactly(false, false, false);
        assertThat(hints(tool("replaceThing"))).containsExactly(false, false, true);
        assertThat(hints(tool("patchThing"))).containsExactly(false, false, false);
        assertThat(hints(tool("deleteThing"))).containsExactly(false, true, true);
    }

    @Test
    void headerParametersAreRejectedAtStartup() {
        String spec = """
                paths:
                  /api/v1/x:
                    get:
                      operationId: x
                      parameters:
                        - name: X-Thing
                          in: header
                          schema:
                            type: string
                """;
        assertThatThrownBy(() -> parse(spec))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("header");
    }

    @Test
    void theRealContractParses() {
        assertThat(McpServerConfig.loadOperations()).isNotEmpty();
    }

    private ToolOperation tool(String name) {
        return requireNonNull(tools.get(name), name);
    }

    private static Map<String, ToolOperation> parse(String yaml) {
        JsonNode spec = new YAMLMapper().readTree(yaml);
        return OpenApiToolParser.parse(spec).stream()
                .collect(Collectors.toMap(ToolOperation::name, Function.identity()));
    }

    private static List<Boolean> hints(ToolOperation tool) {
        return List.of(tool.readOnly(), tool.destructive(), tool.idempotent());
    }

    private static List<String> texts(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
