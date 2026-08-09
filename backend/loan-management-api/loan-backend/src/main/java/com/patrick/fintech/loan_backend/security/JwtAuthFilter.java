package com.patrick.fintech.loan_backend.security;

import com.patrick.fintech.loan_backend.config.JwtUtils;
import com.patrick.fintech.loan_backend.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * JWT authentication filter — reads Bearer token from Authorization header,
 * validates it, and sets Spring Security context.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtils jwtUtils, CustomUserDetailsService userDetailsService) {
        this.jwtUtils         = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtUtils.validateToken(token)) {

                    // A setup-scoped token (issued when a role requiring MFA logs in but
                    // hasn't enrolled yet) can ONLY reach the MFA setup endpoints — this is
                    // what makes MFA for those roles actually mandatory rather than a
                    // suggestion: there is no way to get a real session token without
                    // finishing enrollment first.
                    if (jwtUtils.isSetupToken(token) && !request.getRequestURI().startsWith("/api/mfa")) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write(
                            "{\"success\":false,\"error\":\"Complete MFA setup before accessing this — see /api/mfa/setup\"}");
                        return;
                    }

                    String email = jwtUtils.getEmailFromToken(token);
                    UserDetails ud = userDetailsService.loadUserByUsername(email);
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                // Expected in normal operation — e.g. a token issued before a user changed
                // their email no longer matches anyone, or the token simply expired. The
                // request just proceeds unauthenticated and Spring Security's entry point
                // above returns a clean 401, which the frontend uses to redirect to login.
                logger.debug("JWT did not resolve to an active user (token may be stale/expired): " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
