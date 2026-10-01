package io.github.noahhhx.mimos.api.support;

import io.github.noahhhx.mimos.recipes.recipe.ReadOnlyRecipeException;
import java.net.URI;
import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error raised in Spring MVC to an RFC 9457 problem-details
 * response: domain exceptions (400 invalid, 404 not found or not visible,
 * 403 read-only curated content), Spring MVC's own errors (unsupported or
 * unacceptable media type, unsupported method, unknown path, malformed
 * input), which {@link ResponseEntityExceptionHandler} renders, and anything
 * unexpected as a 500. Security failures are handled by the security chain
 * itself (see ProblemDetailSecurityHandlers).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    @ExceptionHandler({NoSuchElementException.class})
    ProblemDetail notFound(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, "Not found", exception.getMessage());
    }

    @ExceptionHandler(ReadOnlyRecipeException.class)
    ProblemDetail forbidden(ReadOnlyRecipeException exception) {
        return problem(HttpStatus.FORBIDDEN, "Read-only", exception.getMessage());
    }

    /**
     * Anything no other handler maps is a bug: logged with its stack trace
     * here (resolving it keeps it from reaching the container's log), and
     * answered without internals. There is no method security, so no
     * security exception is thrown from a handler; if that changes, those
     * must be left to the security chain rather than turned into 500s.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "The request could not be completed.");
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return malformed(exception, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return malformed(exception, headers, status, request);
    }

    private @Nullable ResponseEntity<Object> malformed(
            Exception exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body =
                problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request body or parameters are malformed.");
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, @Nullable String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://mimos.dev/problems/" + kebabCase(title)));
        return problem;
    }

    private static String kebabCase(String title) {
        return title.replaceAll("\\s+", "-").toLowerCase();
    }
}
