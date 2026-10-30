package com.codingrank.assessment.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_CLIENT_IP = "clientIp";
    public static final String MDC_HTTP_METHOD = "method";
    public static final String MDC_REQUEST_URI = "uri";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        // 1. Resolve or generate correlation / trace ID
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("X-Request-Id");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 2. Resolve client IP (accounting for proxies / reverse proxies)
        String clientIp = resolveClientIp(request);

        // 3. Resolve user identifier if provided in headers
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = "anonymous";
        }

        // 4. Populate MDC context for all log statements in this request thread
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_USER_ID, userId);
        MDC.put(MDC_CLIENT_IP, clientIp);
        MDC.put(MDC_HTTP_METHOD, request.getMethod());
        MDC.put(MDC_REQUEST_URI, request.getRequestURI());

        // 5. Attach traceId to response header for client debugging & support tickets
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            // Log incoming request at DEBUG level (or INFO for non-static endpoints)
            if (!isStaticOrActuator(request.getRequestURI())) {
                log.info("Incoming HTTP Request: {} {} from IP: {} [User: {}]",
                        request.getMethod(), request.getRequestURI(), clientIp, userId);
            }

            filterChain.doFilter(request, response);

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            if (!isStaticOrActuator(request.getRequestURI())) {
                if (status >= 500) {
                    log.error("Completed HTTP Request: {} {} -> Status: {} [Duration: {}ms]",
                            request.getMethod(), request.getRequestURI(), status, duration);
                } else if (status >= 400) {
                    log.warn("Completed HTTP Request: {} {} -> Status: {} [Duration: {}ms]",
                            request.getMethod(), request.getRequestURI(), status, duration);
                } else {
                    log.info("Completed HTTP Request: {} {} -> Status: {} [Duration: {}ms]",
                            request.getMethod(), request.getRequestURI(), status, duration);
                }
            }

            // Always clear MDC to prevent thread-local memory leaks
            MDC.clear();
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isStaticOrActuator(String uri) {
        return uri.startsWith("/h2-console") ||
               uri.startsWith("/favicon.ico") ||
               uri.startsWith("/actuator");
    }
}
