package io.github.noahhhx.mimos.api.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Gives every request an ID and writes one access line per request.
 *
 * <p>The ID comes from the {@code X-Request-Id} request header when it is a
 * bounded token of safe characters, and is generated otherwise; it is echoed
 * as a response header and put in the MDC as {@code requestId} while the
 * request runs, so every log line the request produces carries it.
 *
 * <p>The access line has the method, path, status, duration, the request's
 * {@code Content-Type} and {@code Accept}, and — for any 4xx/5xx — the
 * exception that produced it: one resolved by Spring MVC (our
 * {@code ApiExceptionHandler} or Spring's own resolvers), one the security
 * chain reported via {@link #recordFailure}, or one that escaped the chain.
 * Ordered before the security chain, so 401/403s are logged too.
 */
public final class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String FAILURE_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".FAILURE";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final Supplier<String> ids;
    private final Clock clock;

    public RequestLoggingFilter(Supplier<String> ids, Clock clock) {
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Records why a request failed where Spring MVC's exception handling does
     * not run (the security chain), so the access line can name it.
     */
    public static void recordFailure(HttpServletRequest request, Exception exception) {
        request.setAttribute(FAILURE_ATTRIBUTE, exception);
    }

    static boolean isValid(@Nullable String id) {
        return id != null && VALID_ID.matcher(id).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String id = isValid(supplied) ? supplied : ids.get();
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        long start = clock.millis();
        @Nullable Exception escaped = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            escaped = exception;
            throw exception;
        } finally {
            // An exception escaping the chain becomes a 500 once the container
            // handles it; the response does not say so yet.
            int status = escaped != null && !response.isCommitted() ? 500 : response.getStatus();
            logAccess(request, status, clock.millis() - start, escaped != null ? escaped : failure(request));
            MDC.remove(MDC_KEY);
        }
    }

    private static @Nullable Exception failure(HttpServletRequest request) {
        if (request.getAttribute(FAILURE_ATTRIBUTE) instanceof Exception recorded) {
            return recorded;
        }
        if (request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE) instanceof Exception resolved) {
            return resolved;
        }
        return null;
    }

    private static void logAccess(
            HttpServletRequest request, int status, long durationMs, @Nullable Exception failure) {
        String path = request.getQueryString() == null
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString();
        String contentType = orNone(request.getContentType());
        String accept = orNone(request.getHeader("Accept"));
        @Nullable String cause = status >= 400 && failure != null ? describe(failure) : null;
        StringBuilder message = new StringBuilder()
                .append(request.getMethod())
                .append(' ')
                .append(path)
                .append(" -> ")
                .append(status)
                .append(" (")
                .append(durationMs)
                .append(" ms) content-type=")
                .append(contentType)
                .append(" accept=")
                .append(accept);
        if (cause != null) {
            message.append(" exception=").append(cause);
        }
        LoggingEventBuilder event = log.atLevel(level(request, status))
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", path)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs)
                .addKeyValue("contentType", contentType)
                .addKeyValue("accept", accept);
        if (cause != null) {
            event = event.addKeyValue("exception", cause);
        }
        event.log(message.toString());
    }

    /** Compose polls health every few seconds; successful probes stay out of the INFO log. */
    private static Level level(HttpServletRequest request, int status) {
        if (status >= 500) {
            return Level.ERROR;
        }
        if (status >= 400) {
            return Level.WARN;
        }
        return request.getRequestURI().startsWith("/actuator/health") ? Level.DEBUG : Level.INFO;
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        String text = exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        // Messages can quote request content; keep the access line one line.
        return text.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String orNone(@Nullable String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }
}
