package io.github.noahhhx.mimos.api.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.noahhhx.mimos.api.support.RequestLoggingFilter;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Registers one MCP tool per API operation in the OpenAPI contract
 * ({@link OpenApiToolParser}), so a new endpoint is a tool without further
 * work. Server name, protocol, and instructions are in {@code application.yml}
 * under {@code spring.ai.mcp.server}.
 */
@Configuration(proxyBeanMethods = false)
public class McpServerConfig {

    /** The contract, copied into the jar at build time (see apps/api/pom.xml). */
    static final String SPEC_LOCATION = "contracts/openapi.yaml";

    private static final String CALLER = "mimos.caller";

    @Bean
    List<SyncToolSpecification> mimosTools(ToolDispatcher dispatcher, ObjectMapper objectMapper) {
        return loadOperations().stream()
                .map(operation -> new SyncToolSpecification(
                        tool(operation, objectMapper),
                        (context, request) -> dispatcher.call(operation, caller(context), request.arguments())))
                .toList();
    }

    /**
     * Stands in for Spring AI's MCP mapper of the same name, which needs
     * spring-ai-commons (excluded with the rest of Spring AI's model layer)
     * only to register extra Jackson modules. Same settings otherwise.
     */
    @Bean(name = "mcpServerJsonMapper", defaultCandidate = false)
    JsonMapper mcpServerJsonMapper() {
        return JsonMapper.builder()
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .changeDefaultPropertyInclusion(inclusion ->
                        JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .build();
    }

    /**
     * Replaces the auto-configured transport to hand each call its caller:
     * the {@code /mcp} request's bearer token and request ID.
     */
    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper, McpServerStreamableHttpProperties properties) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(properties.getMcpEndpoint())
                .contextExtractor(McpServerConfig::extractCaller)
                .build();
    }

    static List<ToolOperation> loadOperations() {
        try (InputStream spec = new ClassPathResource(SPEC_LOCATION).getInputStream()) {
            JsonNode tree = new YAMLMapper().readTree(spec);
            return OpenApiToolParser.parse(tree);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the API contract from the classpath: " + SPEC_LOCATION, e);
        }
    }

    private static McpTransportContext extractCaller(ServerRequest request) {
        // /mcp is behind the security chain, so the principal is the caller's validated token.
        if (!(request.principal().orElse(null) instanceof JwtAuthenticationToken authentication)) {
            throw new IllegalStateException("MCP request without a bearer token reached the transport");
        }
        return McpTransportContext.create(Map.of(
                CALLER,
                new ToolDispatcher.Caller(
                        authentication.getToken().getTokenValue(), MDC.get(RequestLoggingFilter.MDC_KEY))));
    }

    private static ToolDispatcher.Caller caller(McpTransportContext context) {
        if (!(context.get(CALLER) instanceof ToolDispatcher.Caller caller)) {
            throw new IllegalStateException("MCP tool call without a caller");
        }
        return caller;
    }

    @SuppressWarnings("unchecked")
    private static McpSchema.Tool tool(ToolOperation operation, ObjectMapper objectMapper) {
        Map<String, Object> inputSchema = objectMapper.treeToValue(operation.inputSchema(), Map.class);
        return McpSchema.Tool.builder(operation.name(), inputSchema)
                .description(operation.description())
                .annotations(McpSchema.ToolAnnotations.builder()
                        .readOnlyHint(operation.readOnly())
                        .destructiveHint(operation.destructive())
                        .idempotentHint(operation.idempotent())
                        .openWorldHint(false)
                        .build())
                .build();
    }
}
