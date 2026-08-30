package io.github.noahhhx.mimos.api.support;

import io.github.noahhhx.mimos.recipes.recipe.ReadOnlyRecipeException;
import java.net.URI;
import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps domain exceptions to RFC 9457 problem-details responses:
 * validation failures (400), not-found/not-visible (404), and attempts to
 * mutate read-only curated content (403). Security failures are handled by
 * the security chain itself (see ProblemDetailSecurityHandlers).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

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

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail unreadable(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request body or parameters are malformed.");
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
