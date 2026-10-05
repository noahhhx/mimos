package io.github.noahhhx.mimos.api.security;

import io.github.noahhhx.mimos.api.support.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Emits RFC 9457 problem-details for authentication and authorization
 * failures from the security filter chain, where the normal MVC
 * exception-handling path does not run. The exception is handed to the
 * access log ({@link RequestLoggingFilter}) for the same reason.
 */
@Component
public final class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Serialized problem shape (RFC 9457); kept explicit and dependency-free. */
    record Problem(URI type, String title, int status, String detail, URI instance, Instant timestamp) {}

    private static final String PROTECTED_RESOURCE_METADATA = "/.well-known/oauth-protected-resource";

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        RequestLoggingFilter.recordFailure(request, authException);
        response.setHeader("WWW-Authenticate", "Bearer resource_metadata=\"" + resourceMetadataUrl(request) + "\"");
        write(response, HttpStatus.UNAUTHORIZED, "A valid bearer token from the Mimos realm is required.", request);
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        RequestLoggingFilter.recordFailure(request, accessDeniedException);
        write(response, HttpStatus.FORBIDDEN, "You are not allowed to do that.", request);
    }

    /**
     * Where an OAuth client (an MCP client, ADR-0014) learns which
     * authorization server to sign in with (RFC 9728): the metadata of the
     * resource that was requested, served by Spring Security's
     * {@code OAuth2ProtectedResourceMetadataFilter}.
     */
    private static String resourceMetadataUrl(HttpServletRequest request) {
        return UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
                .replacePath(PROTECTED_RESOURCE_METADATA + request.getRequestURI())
                .replaceQuery(null)
                .build()
                .toUriString();
    }

    private void write(HttpServletResponse response, HttpStatus status, String detail, HttpServletRequest request)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Not writeValue(response.getWriter(), …): Jackson closes its target,
        // which completes the response before the filters above us (the
        // access log) have finished with it.
        response.getWriter()
                .write(objectMapper.writeValueAsString(new Problem(
                        URI.create("about:blank"),
                        status.getReasonPhrase(),
                        status.value(),
                        detail,
                        URI.create(request.getRequestURI()),
                        Instant.now())));
    }
}
