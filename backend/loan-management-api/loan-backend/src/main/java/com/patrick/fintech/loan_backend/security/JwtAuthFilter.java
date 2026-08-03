package com.patrick.fintech.loan_backend.security;

import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Component;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader("Authorization");

        // --------------------------------------------------------
        // No token
        // --------------------------------------------------------

        if (
            authorizationHeader == null ||
            !authorizationHeader.startsWith("Bearer ")
        ) {

            filterChain.doFilter(
                request,
                response
            );

            return;
        }

        String token =
                authorizationHeader.substring(7);

        try {

            Claims claims =
                    Jwts.parser()
                        .verifyWith(
                            io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                jwtSecret.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                                )
                            )
                        )
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

            // ----------------------------------------------------
            // Extract user identifier
            // ----------------------------------------------------

            String email =
                    claims.getSubject();

            if (
                email == null ||
                email.isBlank()
            ) {

                filterChain.doFilter(
                    request,
                    response
                );

                return;
            }

            // ----------------------------------------------------
            // Find user
            // ----------------------------------------------------

            User user =
                    userRepository
                        .findByEmailIgnoreCase(email)
                        .orElse(null);

            if (user == null) {

                filterChain.doFilter(
                    request,
                    response
                );

                return;
            }

            // ----------------------------------------------------
            // Check if already authenticated
            // ----------------------------------------------------

            if (
                SecurityContextHolder
                    .getContext()
                    .getAuthentication() == null
            ) {

                String role =
                        resolveRole(user);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(
                                new SimpleGrantedAuthority(
                                    "ROLE_" +
                                    role
                                )
                            )
                        );

                SecurityContextHolder
                    .getContext()
                    .setAuthentication(
                        authentication
                    );
            }

        } catch (Exception e) {

            SecurityContextHolder
                .clearContext();

            // Don't expose JWT internals
            // to the client.

        }

        filterChain.doFilter(
            request,
            response
        );
    }

    // ============================================================
    // ROLE
    // ============================================================

    private String resolveRole(
            User user
    ) {

        if (
            user.getRole() == null ||
            user.getRole().getName() == null
        ) {

            return "USER";
        }

        String role =
                user.getRole()
                    .getName()
                    .trim()
                    .toUpperCase();

        // Remove ROLE_ if the database
        // already contains it.

        if (
            role.startsWith("ROLE_")
        ) {

            role =
                    role.substring(5);
        }

        return role;
    }
}