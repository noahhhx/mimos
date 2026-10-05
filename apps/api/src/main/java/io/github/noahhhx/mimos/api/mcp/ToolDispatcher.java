package io.github.noahhhx.mimos.api.mcp;

import static java.util.Objects.requireNonNull;

import io.github.noahhhx.mimos.api.support.RequestLoggingFilter;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Executes a tool call as a request to this API's own HTTP port, as the
 * calling user. Going through HTTP keeps validation, ownership checks,
 * problem-details, and request logging identical to the API's other clients;
 * the inner request carries the outer one's request ID, so both access lines
 * share it.
 */
@Component
public final class ToolDispatcher {

    /** Who is calling, taken from the authenticated {@code /mcp} request. */
    public record Caller(String bearerToken, @Nullable String requestId) {}

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public ToolDispatcher(ObjectMapper objectMapper, Environment environment) {
        // The JDK client: HttpURLConnection, the other built-in, cannot send PATCH.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        // The outer /mcp request holds a server thread while the inner one needs another;
        // with the pool exhausted, a bounded wait fails the call instead of hanging it.
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.http = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    public CallToolResult call(ToolOperation tool, Caller caller, @Nullable Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        String requestId = caller.requestId();
        RestClient.RequestBodySpec request = http.method(tool.method())
                .uri(uri(tool, args))
                .headers(headers -> {
                    headers.setBearerAuth(caller.bearerToken());
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
                    if (requestId != null) {
                        headers.set(RequestLoggingFilter.HEADER, requestId);
                    }
                });
        Object body = args.get(ToolOperation.BODY);
        if (tool.hasBody() && body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(body));
        }
        return request.exchange((req, response) -> {
            int status = response.getStatusCode().value();
            String text = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            if (status >= 400) {
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent(text.isBlank() ? "HTTP " + status : text)
                        .build();
            }
            return CallToolResult.builder()
                    .addTextContent(text.isBlank() ? "Done." : text)
                    .build();
        });
    }

    private URI uri(ToolOperation tool, Map<String, Object> args) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host("127.0.0.1")
                .port(environment.getRequiredProperty("local.server.port"))
                .path(tool.pathTemplate());
        Map<String, Object> variables = new HashMap<>();
        for (String name : tool.pathParams()) {
            // The input schema requires every path parameter, and the SDK validates calls against it.
            variables.put(name, requireNonNull(args.get(name), name));
        }
        for (String name : tool.queryParams()) {
            Object value = args.get(name);
            if (value != null) {
                // As a template variable, so the value is encoded strictly ("+" included).
                builder.queryParam(name, "{" + name + "}");
                variables.put(name, String.valueOf(value));
            }
        }
        return builder.encode().buildAndExpand(variables).toUri();
    }
}
