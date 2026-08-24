package io.github.noahhhx.mimos.api.security;

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
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Emits RFC 9457 problem-details for authentication and authorization
 * failures from the security filter chain, where the normal MVC
 * exception-handling path does not run.
 */
@Component
public final class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Serialized problem shape (RFC 9457); kept explicit and dependency-free. */
    record Problem(URI type, String title, int status, String detail, URI instance, Instant timestamp) {}

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, "A valid bearer token from the Mimos realm is required.", request);
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "You are not allowed to do that.", request);
    }

    private void write(HttpServletResponse response, HttpStatus status, String detail, HttpServletRequest request)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                new Problem(
                        URI.create("about:blank"),
                        status.getReasonPhrase(),
                        status.value(),
                        detail,
                        URI.create(request.getRequestURI()),
                        Instant.now()));
    }
}
