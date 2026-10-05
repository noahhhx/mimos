package io.github.noahhhx.mimos.api.mcp;

import java.util.List;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.node.ObjectNode;

/**
 * One API operation exposed as an MCP tool: what the agent sees (name,
 * description, input schema, hints) and how a call becomes an HTTP request
 * (method, path template, which arguments are path or query parameters, and
 * whether a {@code body} argument is sent).
 *
 * @param name the operation's {@code operationId}
 * @param inputSchema a JSON Schema (2020-12) object whose properties are the
 *     path parameters, the query parameters, and {@code body}
 */
public record ToolOperation(
        String name,
        String description,
        HttpMethod method,
        String pathTemplate,
        List<String> pathParams,
        List<String> queryParams,
        boolean hasBody,
        ObjectNode inputSchema,
        boolean readOnly,
        boolean destructive,
        boolean idempotent) {

    public static final String BODY = "body";

    public ToolOperation {
        pathParams = List.copyOf(pathParams);
        queryParams = List.copyOf(queryParams);
    }
}
