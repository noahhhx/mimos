package io.github.noahhhx.mimos.api.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns the OpenAPI contract into MCP tool operations: every operation under
 * {@code /api/v1/} unless it declares {@code x-mcp: false}. Pure; the spec
 * is the parsed YAML tree.
 *
 * <p>A tool's input schema is one JSON Schema object: path and query
 * parameters become properties of the same name, and the request body
 * becomes a {@code body} property. Component schemas the operation
 * references are copied into {@code $defs} with their refs rewritten, which
 * works because OpenAPI 3.1 schemas are JSON Schema 2020-12.
 */
public final class OpenApiToolParser {

    private static final String API_PREFIX = "/api/v1/";
    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /** OpenAPI path-item keys that hold operations, with the tool hints their HTTP method implies. */
    private enum Verb {
        GET(HttpMethod.GET, true, false, true),
        POST(HttpMethod.POST, false, false, false),
        PUT(HttpMethod.PUT, false, true, true),
        PATCH(HttpMethod.PATCH, false, false, false),
        DELETE(HttpMethod.DELETE, false, true, true);

        final HttpMethod method;
        final boolean readOnly;
        final boolean destructive;
        final boolean idempotent;

        Verb(HttpMethod method, boolean readOnly, boolean destructive, boolean idempotent) {
            this.method = method;
            this.readOnly = readOnly;
            this.destructive = destructive;
            this.idempotent = idempotent;
        }

        String key() {
            return method.name().toLowerCase(Locale.ROOT);
        }
    }

    private OpenApiToolParser() {}

    public static List<ToolOperation> parse(JsonNode spec) {
        List<ToolOperation> tools = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : spec.path("paths").properties()) {
            if (!path.getKey().startsWith(API_PREFIX)) {
                continue;
            }
            JsonNode pathItem = path.getValue();
            for (Verb verb : Verb.values()) {
                JsonNode operation = pathItem.get(verb.key());
                if (operation == null || isOptedOut(operation)) {
                    continue;
                }
                tools.add(toTool(spec, path.getKey(), pathItem, verb, operation));
            }
        }
        return List.copyOf(tools);
    }

    private static boolean isOptedOut(JsonNode operation) {
        JsonNode flag = operation.get("x-mcp");
        return flag != null && flag.isBoolean() && !flag.booleanValue();
    }

    private static ToolOperation toTool(JsonNode spec, String path, JsonNode pathItem, Verb verb, JsonNode operation) {
        String name = require(operation, "operationId", path).asString();
        Map<String, JsonNode> defs = new LinkedHashMap<>();
        ObjectNode properties = NODES.objectNode();
        ArrayNode required = NODES.arrayNode();
        List<String> pathParams = new ArrayList<>();
        List<String> queryParams = new ArrayList<>();

        for (JsonNode parameter : parameters(spec, pathItem, operation)) {
            String paramName = require(parameter, "name", name).asString();
            String location = require(parameter, "in", name).asString();
            switch (location) {
                case "path" -> pathParams.add(paramName);
                case "query" -> queryParams.add(paramName);
                default ->
                    throw new IllegalStateException(
                            name + ": parameters in " + location + " cannot be passed by an MCP tool");
            }
            ObjectNode schema = copyWithRefs(spec, require(parameter, "schema", name), defs);
            if (parameter.hasNonNull("description")) {
                schema.put("description", clean(parameter.get("description").asString()));
            }
            properties.set(paramName, schema);
            if (location.equals("path") || parameter.path("required").asBoolean(false)) {
                required.add(paramName);
            }
        }

        JsonNode requestBody = operation.get("requestBody");
        if (requestBody != null) {
            requestBody = resolve(spec, requestBody);
            JsonNode bodySchema =
                    requestBody.path("content").path("application/json").get("schema");
            if (bodySchema == null) {
                throw new IllegalStateException(name + ": only application/json request bodies are supported");
            }
            ObjectNode schema = copyWithRefs(spec, bodySchema, defs);
            if (requestBody.hasNonNull("description")) {
                schema.put("description", clean(requestBody.get("description").asString()));
            }
            properties.set(ToolOperation.BODY, schema);
            if (requestBody.path("required").asBoolean(false)) {
                required.add(ToolOperation.BODY);
            }
        }

        ObjectNode inputSchema = NODES.objectNode();
        inputSchema.put("type", "object");
        inputSchema.set("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.set("required", required);
        }
        // A misplaced argument (a body field at the top level) is reported, not dropped.
        inputSchema.put("additionalProperties", false);
        if (!defs.isEmpty()) {
            ObjectNode defsNode = inputSchema.putObject("$defs");
            defs.forEach(defsNode::set);
        }
        return new ToolOperation(
                name,
                description(operation),
                verb.method,
                path,
                pathParams,
                queryParams,
                requestBody != null,
                inputSchema,
                verb.readOnly,
                verb.destructive,
                verb.idempotent);
    }

    /** Path-item parameters, overridden by operation parameters with the same name and location. */
    private static List<JsonNode> parameters(JsonNode spec, JsonNode pathItem, JsonNode operation) {
        Map<String, JsonNode> byKey = new LinkedHashMap<>();
        for (JsonNode source : List.of(pathItem.path("parameters"), operation.path("parameters"))) {
            for (JsonNode parameter : source) {
                JsonNode resolved = resolve(spec, parameter);
                byKey.put(
                        resolved.path("in").asString() + ":"
                                + resolved.path("name").asString(),
                        resolved);
            }
        }
        return List.copyOf(byKey.values());
    }

    private static String description(JsonNode operation) {
        List<String> parts = new ArrayList<>();
        for (String field : List.of("summary", "description")) {
            if (operation.hasNonNull(field)) {
                parts.add(clean(operation.get(field).asString()));
            }
        }
        return String.join("\n\n", parts);
    }

    /** Folded YAML scalars keep a trailing newline; agents get the text alone. */
    private static String clean(String text) {
        return text.strip();
    }

    /** Follows a local {@code $ref} (to a parameter or request body) to the node it names. */
    private static JsonNode resolve(JsonNode spec, JsonNode node) {
        if (!node.hasNonNull("$ref")) {
            return node;
        }
        String ref = node.get("$ref").asString();
        @Nullable JsonNode target = ref.startsWith("#/") ? spec.at(ref.substring(1)) : null;
        if (target == null || target.isMissingNode()) {
            throw new IllegalStateException("unresolvable $ref: " + ref);
        }
        return target;
    }

    /**
     * Deep-copies a schema, rewriting {@code #/components/schemas/X} to
     * {@code #/$defs/X} and copying each referenced component (transitively)
     * into {@code defs}.
     */
    private static ObjectNode copyWithRefs(JsonNode spec, JsonNode schema, Map<String, JsonNode> defs) {
        if (!schema.isObject()) {
            throw new IllegalStateException("schema must be an object: " + schema);
        }
        return (ObjectNode) rewrite(spec, schema, defs);
    }

    private static JsonNode rewrite(JsonNode spec, JsonNode node, Map<String, JsonNode> defs) {
        if (node.isObject()) {
            ObjectNode copy = NODES.objectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (field.getKey().equals("$ref") && field.getValue().isString()) {
                    copy.put("$ref", defRef(spec, field.getValue().asString(), defs));
                } else {
                    copy.set(field.getKey(), rewrite(spec, field.getValue(), defs));
                }
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = NODES.arrayNode();
            node.forEach(element -> copy.add(rewrite(spec, element, defs)));
            return copy;
        }
        return node.deepCopy();
    }

    private static String defRef(JsonNode spec, String ref, Map<String, JsonNode> defs) {
        if (!ref.startsWith(SCHEMA_REF_PREFIX)) {
            throw new IllegalStateException("only component schema refs are supported in schemas: " + ref);
        }
        String schemaName = ref.substring(SCHEMA_REF_PREFIX.length());
        if (!defs.containsKey(schemaName)) {
            JsonNode component = spec.path("components").path("schemas").get(schemaName);
            if (component == null) {
                throw new IllegalStateException("unresolvable $ref: " + ref);
            }
            // Reserve the name first so a self-referencing schema terminates.
            defs.put(schemaName, NODES.objectNode());
            defs.put(schemaName, rewrite(spec, component, defs));
        }
        return "#/$defs/" + schemaName;
    }

    private static JsonNode require(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalStateException(context + ": missing " + field);
        }
        return value;
    }
}
