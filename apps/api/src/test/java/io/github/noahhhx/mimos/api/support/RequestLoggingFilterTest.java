package io.github.noahhhx.mimos.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.DispatcherServlet;

class RequestLoggingFilterTest {

    /** Each read advances 7 ms, so a request takes 7 ms. */
    private static final class SteppingClock extends Clock {
        private final AtomicLong millis = new AtomicLong();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.getAndAdd(7));
        }
    }

    private final RequestLoggingFilter filter = new RequestLoggingFilter(() -> "generated-id", new SteppingClock());
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/recipes");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void captureLog() {
        logger.setLevel(Level.DEBUG);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(events);
        logger.setLevel(null);
    }

    @Test
    void generatesAnIdWhenNoneIsSuppliedAndScopesItToTheRequest() throws Exception {
        List<@Nullable String> seen = new ArrayList<>();

        filter.doFilter(request, response, (req, res) -> seen.add(MDC.get(RequestLoggingFilter.MDC_KEY)));

        assertThat(response.getHeader(RequestLoggingFilter.HEADER)).isEqualTo("generated-id");
        assertThat(seen).containsExactly("generated-id");
        assertThat(MDC.get(RequestLoggingFilter.MDC_KEY)).isNull();
        assertThat(single().getMDCPropertyMap()).containsEntry(RequestLoggingFilter.MDC_KEY, "generated-id");
    }

    @Test
    void echoesAWellFormedSuppliedId() throws Exception {
        request.addHeader(RequestLoggingFilter.HEADER, "3f2b9c1e-0d4a-4b7e-9a51-6c2f8e7d1a90");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.HEADER)).isEqualTo("3f2b9c1e-0d4a-4b7e-9a51-6c2f8e7d1a90");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "has space",
                "line\nbreak",
                "<script>",
                "id;drop",
                "a1234567890123456789012345678901234567890123456789012345678901234"
            })
    void replacesAMalformedSuppliedId(String supplied) throws Exception {
        request.addHeader(RequestLoggingFilter.HEADER, supplied);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.HEADER)).isEqualTo("generated-id");
    }

    @Test
    void writesOneAccessLinePerRequest() throws Exception {
        request.setQueryString("q=soup");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.addHeader("Accept", "application/json");

        filter.doFilter(request, response, new MockFilterChain());

        ILoggingEvent event = single();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .isEqualTo("GET /api/v1/recipes?q=soup -> 200 (7 ms) content-type=application/json"
                        + " accept=application/json");
        assertThat(event.getKeyValuePairs())
                .extracting(pair -> pair.key + "=" + pair.value)
                .containsExactly(
                        "method=GET",
                        "path=/api/v1/recipes?q=soup",
                        "status=200",
                        "durationMs=7",
                        "contentType=application/json",
                        "accept=application/json");
    }

    @Test
    void namesTheExceptionSpringMvcResolved() throws Exception {
        request.setMethod("POST");

        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(
                    DispatcherServlet.EXCEPTION_ATTRIBUTE,
                    new HttpMediaTypeNotSupportedException("Content-Type 'text/plain' is not supported"));
            response.setStatus(415);
        });

        ILoggingEvent event = single();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .isEqualTo("POST /api/v1/recipes -> 415 (7 ms) content-type=<none> accept=<none>"
                        + " exception=HttpMediaTypeNotSupportedException: Content-Type 'text/plain' is not supported");
    }

    @Test
    void namesTheExceptionTheSecurityChainRecorded() throws Exception {
        filter.doFilter(request, response, (req, res) -> {
            RequestLoggingFilter.recordFailure(
                    request, new InsufficientAuthenticationException("Full authentication is required"));
            response.setStatus(401);
        });

        assertThat(single().getFormattedMessage())
                .endsWith("-> 401 (7 ms) content-type=<none> accept=<none>"
                        + " exception=InsufficientAuthenticationException: Full authentication is required");
    }

    @Test
    void logsAnEscapingExceptionAsA500AndRethrowsIt() {
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
                    throw new ServletException("boom\nsecond line");
                }))
                .isInstanceOf(ServletException.class);

        ILoggingEvent event = single();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .endsWith("-> 500 (7 ms) content-type=<none> accept=<none>"
                        + " exception=ServletException: boom\\nsecond line");
        assertThat(MDC.get(RequestLoggingFilter.MDC_KEY)).isNull();
    }

    @Test
    void successfulHealthProbesLogAtDebug() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), response, new MockFilterChain());

        assertThat(single().getLevel()).isEqualTo(Level.DEBUG);
    }

    private ILoggingEvent single() {
        assertThat(events.list).hasSize(1);
        return events.list.getFirst();
    }
}
