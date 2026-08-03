package com.patrick.fintech.loan_backend.security;

import com.patrick.fintech.loan_backend.model.RegulatoryApiClient;
import com.patrick.fintech.loan_backend.repository.RegulatoryApiClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Component
public class RegulatoryApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Api-Key";
    private static final String PROTECTED_PREFIX = "/api/regulatory/external/";

    private final RegulatoryApiClientRepository repository;
    private final PasswordEncoder passwordEncoder;

    public RegulatoryApiKeyAuthFilter(RegulatoryApiClientRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            unauthorized(response, "Missing X-Api-Key header");
            return;
        }

        String lookupPrefix = rawKey.substring(0, Math.min(16, rawKey.length()));
        Optional<RegulatoryApiClient> match = repository.findByKeyPrefix(lookupPrefix);

        if (match.isEmpty() || !passwordEncoder.matches(rawKey, match.get().getKeyHash())) {
            unauthorized(response, "Invalid API key");
            return;
        }

        RegulatoryApiClient client = match.get();
        if (!client.isCurrentlyValid()) {
            unauthorized(response, "This API key has been revoked or has expired");
            return;
        }

       
        boolean isBnrRoute = request.getRequestURI().startsWith(PROTECTED_PREFIX + "bnr");
        boolean isCbRoute = request.getRequestURI().startsWith(PROTECTED_PREFIX + "credit-bureau");

        if (!isBnrRoute && !isCbRoute) {
            unauthorized(response, "Unrecognized regulatory API route");
            return;
        }

        boolean typeMatches = (isBnrRoute && client.getClientType() == RegulatoryApiClient.ClientType.BNR)
            || (isCbRoute && client.getClientType() == RegulatoryApiClient.ClientType.CREDIT_BUREAU);
        if (!typeMatches) {
            unauthorized(response, "This API key is not authorized for this report type");
            return;
        }

        client.setLastUsedAt(LocalDateTime.now());
        client.setLastUsedIp(request.getRemoteAddr());
        repository.save(client);

        String authority = client.getClientType() == RegulatoryApiClient.ClientType.BNR
            ? "ROLE_BNR_API" : "ROLE_CREDIT_BUREAU_API";

        RegulatoryApiPrincipal principal = new RegulatoryApiPrincipal(client);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(authority)));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"error\":\"" + message + "\"}");
    }
}