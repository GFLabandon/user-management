package io.github.gflabandon.counselor.web;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Records outcomes without request paths, query strings, headers, bodies or exception messages. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        String previous = MDC.get("requestId");
        MDC.put("requestId", requestId);
        request.setAttribute("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        try {
            chain.doFilter(request, response);
        } catch (ServletException | RuntimeException failure) {
            int status = causedByDataAccess(failure) ? 503 : 500;
            // A message/stack from JDBC or template rendering may contain personal data.
            log.error("request_failed status={} type={}", status, failure.getClass().getSimpleName());
            if (response.isCommitted()) throw new ServletException("Request failed; reference " + requestId);
            response.resetBuffer();
            response.sendError(status);
        } finally {
            if (!request.getRequestURI().startsWith(request.getContextPath() + "/actuator/")) {
                log.info("request_completed method={} status={} duration_ms={}",
                        safeMethod(request.getMethod()), response.getStatus(), (System.nanoTime() - started) / 1_000_000);
            }
            if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous);
        }
    }

    private static boolean causedByDataAccess(Throwable failure) {
        for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause()) {
            if (failure instanceof DataAccessException) return true;
        }
        return false;
    }

    private static String safeMethod(String method) {
        return switch (method) {
            case "GET", "POST", "HEAD", "PUT", "PATCH", "DELETE", "OPTIONS" -> method;
            default -> "OTHER";
        };
    }
}
