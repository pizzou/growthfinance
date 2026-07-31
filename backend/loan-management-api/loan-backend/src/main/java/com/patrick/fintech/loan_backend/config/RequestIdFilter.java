package com.patrick.fintech.loan_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Without this, every log line just says what happened, never which request it happened for —
 * tracing a single user's error through the logs (especially once this runs behind a load
 * balancer with more than one backend instance) means grepping timestamps and hoping. This
 * stamps every request with an ID that's attached to every log line logged during it (via SLF4J
 * MDC, which the JSON log encoder in logback-spring.xml includes automatically) and echoed back
 * as a response header, so a borrower or officer reporting "it broke" can hand you a request ID
 * that jumps straight to the right log lines.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Trust an inbound ID from our own load balancer/reverse proxy (so a request can be
        // traced across it and the app), but always generate our own if none was supplied —
        // never trust an arbitrary client-supplied value as-is without at least a sanity bound.
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
