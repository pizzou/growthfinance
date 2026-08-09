
package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.security.JwtAuthFilter;
import com.patrick.fintech.loan_backend.security.RateLimitFilter;
import com.patrick.fintech.loan_backend.security.RegulatoryApiKeyAuthFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtFilter;
    private final RegulatoryApiKeyAuthFilter regulatoryApiKeyAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    @Value("${app.cors.allowed-origins:https://growthfinance-six.vercel.app}")
    private String allowedOrigins;


    // ============================================================
    // SECURITY FILTER CHAIN
    // ============================================================

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http
    ) throws Exception {

        http

            // ====================================================
            // CORS
            // ====================================================

            .cors(cors ->
                cors.configurationSource(corsSource())
            )

            // ====================================================
            // CSRF
            // ====================================================

            .csrf(csrf ->
                csrf.disable()
            )

            // ====================================================
            // SESSION
            // ====================================================

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            // ====================================================
            // SECURITY EXCEPTION HANDLING
            //
            // IMPORTANT:
            // Security exceptions must be handled here.
            // GlobalExceptionHandler is not the correct place for
            // authorization failures occurring inside Spring Security.
            // ====================================================

            .exceptionHandling(exception ->
                exception

                    // --------------------------------------------
                    // 401 - NOT AUTHENTICATED
                    // --------------------------------------------

                    .authenticationEntryPoint(
                        authenticationEntryPoint()
                    )

                    // --------------------------------------------
                    // 403 - AUTHENTICATED BUT NOT AUTHORIZED
                    // --------------------------------------------

                    .accessDeniedHandler(
                        accessDeniedHandler()
                    )
            )

            // ====================================================
            // AUTHORIZATION
            // ====================================================

            .authorizeHttpRequests(authorize ->
                authorize

                    // --------------------------------------------
                    // PUBLIC ENDPOINTS
                    // --------------------------------------------

                    .requestMatchers(
                        "/api/auth/**",
                        "/h2-console/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs/**",
                        "/v3/api-docs/**",
                        "/actuator/health",
                        "/api/public/**",
                        "/public/**"
                    )
                    .permitAll()

                    // --------------------------------------------
                    // REGULATORY ENDPOINTS
                    //
                    // Keep these authenticated.
                    // Actual role permissions can additionally
                    // be controlled with @PreAuthorize.
                    // --------------------------------------------

                    .requestMatchers(
                        "/api/regulatory/**"
                    )
                    .authenticated()

                    // --------------------------------------------
                    // EVERYTHING ELSE
                    // --------------------------------------------

                    .anyRequest()
                    .authenticated()
            )

            // ====================================================
            // H2 CONSOLE
            // ====================================================

            .headers(headers ->
                headers.frameOptions(
                    frame -> frame.sameOrigin()
                )
            )

            // ====================================================
            // RATE LIMIT
            // ====================================================

            .addFilterBefore(
                rateLimitFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            // ====================================================
            // JWT
            // ====================================================

            .addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            // ====================================================
            // REGULATORY API KEY
            // ====================================================

            .addFilterBefore(
                regulatoryApiKeyAuthFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }


    // ============================================================
    // 401 AUTHENTICATION ENTRY POINT
    // ============================================================

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {

        return (request, response, authException) -> {

            response.setStatus(
                HttpServletResponse.SC_UNAUTHORIZED
            );

            response.setCharacterEncoding("UTF-8");

            response.setContentType(
                "application/json"
            );

            response.getWriter().write(
                """
                {
                  "success": false,
                  "error": "Authentication required. Please log in again."
                }
                """
            );
        };
    }


    // ============================================================
    // 403 ACCESS DENIED HANDLER
    // ============================================================

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {

        return (request, response, accessDeniedException) -> {

            response.setStatus(
                HttpServletResponse.SC_FORBIDDEN
            );

            response.setCharacterEncoding("UTF-8");

            response.setContentType(
                "application/json"
            );

            response.getWriter().write(
                """
                {
                  "success": false,
                  "error": "Access denied.",
                  "detail": "Your account is authenticated but does not have permission to perform this action."
                }
                """
            );
        };
    }


    // ============================================================
    // CORS
    // ============================================================

    @Bean
    public CorsConfigurationSource corsSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        List<String> origins =
                Arrays.stream(
                        allowedOrigins.split(",")
                )
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();

        configuration.setAllowedOrigins(
                origins
        );

        configuration.setAllowedMethods(
                List.of(
                    "GET",
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE",
                    "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setExposedHeaders(
                List.of(
                    "Content-Disposition",
                    "Content-Type",
                    "X-Request-ID"
                )
        );

        configuration.setAllowCredentials(
                true
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }


    // ============================================================
    // AUTHENTICATION MANAGER
    // ============================================================

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration.getAuthenticationManager();
    }
}
