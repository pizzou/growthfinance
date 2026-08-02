package com.patrick.fintech.loan_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, String pathSuffix, RateLimiter limiter, String label) {}

    private final List<Rule> rules = List.of(
        
        new Rule("POST", "/api/auth/login", new RateLimiter(20, TimeUnit.MINUTES.toMillis(5)), "login"),

       
        new Rule("POST", "/api/auth/register", new RateLimiter(5, TimeUnit.HOURS.toMillis(1)), "register"),

        
        new Rule("POST", "/api/auth/forgot-password", new RateLimiter(5, TimeUnit.HOURS.toMillis(1)), "forgot-password"),
        new Rule("POST", "/api/auth/reset-password", new RateLimiter(10, TimeUnit.HOURS.toMillis(1)), "reset-password"),

       
        new Rule("POST", "/resend-otp", new RateLimiter(3, TimeUnit.MINUTES.toMillis(15)), "esignature-otp-resend"),

        
        new Rule("ANY", "/api/public/", new RateLimiter(60, TimeUnit.MINUTES.toMillis(1)), "public-general")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        for (Rule rule : rules) {
            boolean methodMatches = rule.method().equals("ANY") || rule.method().equalsIgnoreCase(method);
            if (!methodMatches || !path.contains(rule.pathSuffix())) continue;

            String key = rule.label() + ":" + clientIp(request);
            if (!rule.limiter().tryAcquire(key)) {
                long retryAfter = rule.limiter().secondsUntilReset(key);
                log.warn("Rate limit exceeded: rule={} ip={} path={}", rule.label(), clientIp(request), path);
                response.setStatus(429); // 429 Too Many Requests
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"success\":false,\"error\":\"Too many requests. Please try again in a moment.\"}");
                return;
            }
            
            break;
        }

        chain.doFilter(request, response);
    }

    
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

   
    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    public void cleanup() {
        for (Rule rule : rules) {
            rule.limiter().evictStale();
        }
    }
}