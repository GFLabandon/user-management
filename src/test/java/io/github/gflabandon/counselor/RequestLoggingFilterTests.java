package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.assertThat;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.gflabandon.counselor.web.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTests {
    @Test void createsNewServerIdAndRestoresThreadContext() throws Exception {
        var filter = new RequestLoggingFilter();
        var request = new MockHttpServletRequest("GET", "/counselors");
        request.addHeader("X-Request-ID", "untrusted-client-id");
        var response = new MockHttpServletResponse();
        MDC.put("requestId", "outer-context");
        try {
            filter.doFilter(request, response, (req, res) -> {
                assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-ID"));
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(403);
            });
            assertThat(response.getHeader("X-Request-ID")).matches("[0-9a-f-]{36}");
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(MDC.get("requestId")).isEqualTo("outer-context");
            var second = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/login"), second, (req, res) -> {});
            assertThat(second.getHeader("X-Request-ID")).isNotEqualTo(response.getHeader("X-Request-ID"));
        } finally { MDC.clear(); }
    }

    @Test void databaseFailureHasCorrelated503WithoutSensitiveMessageOrRequestData() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        var events = new ListAppender<ILoggingEvent>(); events.start(); logger.addAppender(events);
        var request = new MockHttpServletRequest("POST", "/secret-path");
        request.setQueryString("password=secret-query");
        request.setContent("secret-body".getBytes());
        request.addHeader("Cookie", "secret-session");
        var response = new MockHttpServletResponse();
        try {
            new RequestLoggingFilter().doFilter(request, response, (req, res) -> {
                throw new DataAccessResourceFailureException("secret-sql-and-password");
            });
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(MDC.get("requestId")).isNull();
            assertThat(events.list).anyMatch(e -> e.getFormattedMessage().contains("request_failed status=503"));
            for (var e : events.list) {
                assertThat(e.getFormattedMessage()).doesNotContain("secret-");
                assertThat(e.getThrowableProxy()).isNull();
                assertThat(e.getMDCPropertyMap()).containsEntry("requestId", response.getHeader("X-Request-ID"));
            }
        } finally { logger.detachAppender(events); events.stop(); }
    }

    @Test void unexpectedFailureReturnsGeneric500AndClearsContext() throws Exception {
        var response = new MockHttpServletResponse();
        new RequestLoggingFilter().doFilter(new MockHttpServletRequest("GET", "/counselors"), response,
                (req, res) -> { throw new IllegalStateException("private internal details"); });
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).isNull();
        assertThat(MDC.get("requestId")).isNull();
    }
}
